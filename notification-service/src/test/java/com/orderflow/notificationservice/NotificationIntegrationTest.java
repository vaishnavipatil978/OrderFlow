package com.orderflow.notificationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.security.JwtService;
import com.orderflow.notificationservice.messaging.OrderEvent;
import com.orderflow.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over real Kafka and Postgres: an order event published to order.events becomes a
 * stored notification visible through the API, and redelivery of the same event does not duplicate it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NotificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void confirmedOrderEventBecomesANotificationVisibleToTheCustomer() throws Exception {
        String customer = "jane-" + UUID.randomUUID() + "@orderflow.dev";
        OrderEvent event = event(customer, OrderEvent.ORDER_CONFIRMED, null);

        kafkaTemplate.send("order.events", event.orderId().toString(), objectMapper.writeValueAsString(event)).get();

        await().atMost(Duration.ofSeconds(30))
                .until(() -> notificationRepository.existsByEventId(event.eventId()));
        mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + tokenFor(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].subject").value("Your order is confirmed"));
    }

    @Test
    void redeliveredEventIsNotNotifiedTwice() throws Exception {
        String customer = "jane-" + UUID.randomUUID() + "@orderflow.dev";
        OrderEvent event = event(customer, OrderEvent.ORDER_CANCELLED, "Payment declined");
        String payload = objectMapper.writeValueAsString(event);

        kafkaTemplate.send("order.events", event.orderId().toString(), payload).get();
        kafkaTemplate.send("order.events", event.orderId().toString(), payload).get();

        await().atMost(Duration.ofSeconds(30))
                .until(() -> notificationRepository.existsByEventId(event.eventId()));
        long count = notificationRepository.findByRecipientOrderByCreatedAtDesc(customer, Pageable.unpaged())
                .getTotalElements();
        assertThat(count).isEqualTo(1);
    }

    private OrderEvent event(String customer, String type, String reason) {
        return new OrderEvent(UUID.randomUUID(), type, UUID.randomUUID(), customer,
                new BigDecimal("30.00"), "CONFIRMED", reason, Instant.now());
    }

    private String tokenFor(String email) {
        return jwtService.generateAccessToken(email, UUID.randomUUID().toString(),
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }
}
