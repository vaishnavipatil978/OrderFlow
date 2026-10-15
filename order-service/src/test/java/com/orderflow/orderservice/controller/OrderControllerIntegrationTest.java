package com.orderflow.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.response.ApiResponse;
import com.orderflow.common.security.JwtService;
import com.orderflow.orderservice.AbstractIntegrationTest;
import com.orderflow.orderservice.FeignTestErrors;
import com.orderflow.orderservice.client.ChargePaymentRequest;
import com.orderflow.orderservice.client.InventoryClient;
import com.orderflow.orderservice.client.PaymentClient;
import com.orderflow.orderservice.client.PaymentView;
import com.orderflow.orderservice.client.ProductClient;
import com.orderflow.orderservice.client.ProductView;
import com.orderflow.orderservice.outbox.OutboxEvent;
import com.orderflow.orderservice.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full HTTP flows through the saga with real Postgres and Kafka. The three downstream services are
 * mocked at the Feign boundary: their own behaviour is covered by their own suites, and this suite is
 * about how order-service reacts to each outcome.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrderControllerIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private ProductClient productClient;

    @MockBean
    private InventoryClient inventoryClient;

    @MockBean
    private PaymentClient paymentClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void successfulSagaConfirmsOrderAndPublishesOutboxEvent() throws Exception {
        UUID productId = stubActiveProduct();
        when(paymentClient.charge(any(ChargePaymentRequest.class)))
                .thenReturn(ApiResponse.success(new PaymentView(null, BigDecimal.TEN, "CAPTURED", null)));

        MvcResult result = placeOrder(customer("jane@orderflow.dev"), productId, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andReturn();
        String orderId = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();

        verify(inventoryClient).commit(UUID.fromString(orderId));
        await().atMost(Duration.ofSeconds(30)).until(() -> outboxPublished(UUID.fromString(orderId)));
    }

    @Test
    void insufficientStockCancelsOrderWithoutCharging() throws Exception {
        UUID productId = stubActiveProduct();
        when(inventoryClient.reserve(any())).thenThrow(FeignTestErrors.status(409));
        String token = customer("jane@orderflow.dev");

        placeOrder(token, productId, 5)
                .andExpect(status().isConflict());

        verify(paymentClient, never()).charge(any());
        mockMvc.perform(get("/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.content[0].failureReason")
                        .value("Insufficient stock for one or more items"));
    }

    @Test
    void declinedPaymentReleasesReservedStock() throws Exception {
        UUID productId = stubActiveProduct();
        when(paymentClient.charge(any(ChargePaymentRequest.class)))
                .thenReturn(ApiResponse.success(new PaymentView(null, BigDecimal.TEN, "DECLINED", "over limit")));

        placeOrder(customer("jane@orderflow.dev"), productId, 1)
                .andExpect(status().isConflict());

        verify(inventoryClient).release(any(UUID.class));
        verify(inventoryClient, never()).commit(any());
    }

    @Test
    void failedCommitRefundsPaymentAndReleasesStock() throws Exception {
        UUID productId = stubActiveProduct();
        when(paymentClient.charge(any(ChargePaymentRequest.class)))
                .thenReturn(ApiResponse.success(new PaymentView(null, BigDecimal.TEN, "CAPTURED", null)));
        when(inventoryClient.commit(any(UUID.class))).thenThrow(FeignTestErrors.status(500));

        placeOrder(customer("jane@orderflow.dev"), productId, 1)
                .andExpect(status().isServiceUnavailable());

        verify(paymentClient).refund(any(UUID.class));
        verify(inventoryClient).release(any(UUID.class));
    }

    @Test
    void customerCannotReadAnotherCustomersOrder() throws Exception {
        UUID productId = stubActiveProduct();
        when(paymentClient.charge(any(ChargePaymentRequest.class)))
                .thenReturn(ApiResponse.success(new PaymentView(null, BigDecimal.TEN, "CAPTURED", null)));
        MvcResult result = placeOrder(customer("owner@orderflow.dev"), productId, 1).andReturn();
        String orderId = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();

        mockMvc.perform(get("/orders/" + orderId).header("Authorization", "Bearer " + customer("intruder@orderflow.dev")))
                .andExpect(status().isNotFound());
    }

    @Test
    void placingAnOrderRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(UUID.randomUUID(), 1)))
                .andExpect(status().isUnauthorized());
    }

    private UUID stubActiveProduct() {
        UUID productId = UUID.randomUUID();
        when(productClient.getById(productId))
                .thenReturn(ApiResponse.success(new ProductView(productId, "Widget", new BigDecimal("4.99"), true)));
        return productId;
    }

    private org.springframework.test.web.servlet.ResultActions placeOrder(String token, UUID productId, int quantity)
            throws Exception {
        return mockMvc.perform(post("/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody(productId, quantity)));
    }

    private String orderBody(UUID productId, int quantity) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("productId", productId, "quantity", quantity))));
    }

    private boolean outboxPublished(UUID orderId) {
        return outboxEventRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(orderId))
                .map(OutboxEvent::isPublished)
                .findFirst()
                .orElse(false);
    }

    private String customer(String email) {
        return jwtService.generateAccessToken(email, UUID.randomUUID().toString(),
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }
}
