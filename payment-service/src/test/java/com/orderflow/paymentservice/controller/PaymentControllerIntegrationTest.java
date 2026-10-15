package com.orderflow.paymentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.security.JwtService;
import com.orderflow.paymentservice.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Test
    void chargeWithinLimitIsCaptured() throws Exception {
        UUID orderId = UUID.randomUUID();
        mockMvc.perform(post("/payments/charge")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargeBody(orderId, "120.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CAPTURED"));
    }

    @Test
    void chargeOverLimitIsDeclinedWithReason() throws Exception {
        UUID orderId = UUID.randomUUID();
        mockMvc.perform(post("/payments/charge")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargeBody(orderId, "20000.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLINED"))
                .andExpect(jsonPath("$.data.declineReason").isNotEmpty());
    }

    @Test
    void refundReversesACapturedCharge() throws Exception {
        UUID orderId = UUID.randomUUID();
        mockMvc.perform(post("/payments/charge")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargeBody(orderId, "15.00")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/payments/" + orderId + "/refund").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));
    }

    @Test
    void refundOfUnknownOrderIsNotFound() throws Exception {
        mockMvc.perform(post("/payments/" + UUID.randomUUID() + "/refund").header("Authorization", "Bearer " + token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void paymentEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(post("/payments/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargeBody(UUID.randomUUID(), "10.00")))
                .andExpect(status().isUnauthorized());
    }

    private String chargeBody(UUID orderId, String amount) throws Exception {
        return objectMapper.writeValueAsString(Map.of("orderId", orderId, "amount", new BigDecimal(amount)));
    }

    private String token() {
        return jwtService.generateAccessToken("customer@orderflow.dev", UUID.randomUUID().toString(),
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }
}
