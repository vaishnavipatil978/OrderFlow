package com.orderflow.inventoryservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.security.JwtService;
import com.orderflow.inventoryservice.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class InventoryControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Test
    void reserveThenCommitReducesOnHandStock() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        setStock(productId, 5);

        reserve(orderId, productId, 3);
        assertStock(productId, "$.data.available", 2);

        mockMvc.perform(post("/inventory/reservations/" + orderId + "/commit").header("Authorization", "Bearer " + customerToken()))
                .andExpect(status().isOk());
        assertStock(productId, "$.data.quantityOnHand", 2);
        assertStock(productId, "$.data.quantityReserved", 0);
    }

    @Test
    void releaseRestoresAvailability() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        setStock(productId, 5);
        reserve(orderId, productId, 3);

        mockMvc.perform(post("/inventory/reservations/" + orderId + "/release").header("Authorization", "Bearer " + customerToken()))
                .andExpect(status().isOk());

        assertStock(productId, "$.data.available", 5);
    }

    @Test
    void reserveBeyondAvailableIsConflict() throws Exception {
        UUID productId = UUID.randomUUID();
        setStock(productId, 2);

        mockMvc.perform(post("/inventory/reservations")
                        .header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveBody(UUID.randomUUID(), productId, 5)))
                .andExpect(status().isConflict());
    }

    @Test
    void customerCannotSetStock() throws Exception {
        mockMvc.perform(put("/inventory/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantityOnHand", 5))))
                .andExpect(status().isForbidden());
    }

    @Test
    void stockEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/inventory/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    private void setStock(UUID productId, int quantity) throws Exception {
        mockMvc.perform(put("/inventory/" + productId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantityOnHand", quantity))))
                .andExpect(status().isOk());
    }

    private void reserve(UUID orderId, UUID productId, int quantity) throws Exception {
        mockMvc.perform(post("/inventory/reservations")
                        .header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveBody(orderId, productId, quantity)))
                .andExpect(status().isOk());
    }

    private void assertStock(UUID productId, String jsonPath, int expected) throws Exception {
        mockMvc.perform(get("/inventory/" + productId).header("Authorization", "Bearer " + customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(jsonPath).value(expected));
    }

    private String reserveBody(UUID orderId, UUID productId, int quantity) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "orderId", orderId,
                "items", List.of(Map.of("productId", productId, "quantity", quantity))));
    }

    private String adminToken() {
        return jwtService.generateAccessToken("admin@orderflow.dev", UUID.randomUUID().toString(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private String customerToken() {
        return jwtService.generateAccessToken("customer@orderflow.dev", UUID.randomUUID().toString(),
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }
}
