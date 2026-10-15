package com.orderflow.productservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.security.JwtService;
import com.orderflow.productservice.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the whole point of extracting JWT validation into common-library: this service
 * never talks to identity-service, isn't running it, and doesn't share any Java type with
 * it beyond common-library - yet a token minted here with JwtService (the same class
 * identity-service uses to issue tokens, configured with the same secret via
 * src/test/resources/application.yml) is accepted exactly as if identity-service had issued
 * it. That's the decoupling the refactor was for.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ProductControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Test
    void anyoneCanSearchAndReadWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/products")).andExpect(status().isOk());
    }

    @Test
    void creatingAProductRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newProductRequest("NO-AUTH-1"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerTokenCannotCreateAProduct() throws Exception {
        String customerToken = tokenFor("customer@orderflow.dev", "ROLE_CUSTOMER");

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newProductRequest("CUST-1"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTokenCanCreateUpdateAndDeleteAProduct() throws Exception {
        String adminToken = tokenFor("admin@orderflow.dev", "ROLE_ADMIN");

        MvcResult createResult = mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newProductRequest("ADMIN-1"))))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/id").asText();

        mockMvc.perform(get("/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("ADMIN-1"));

        mockMvc.perform(put("/products/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "Updated Widget", "sku", "ADMIN-1",
                                        "price", BigDecimal.valueOf(19.99), "category", "Tools"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Widget"));

        mockMvc.perform(delete("/products/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/products").param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.sku == 'ADMIN-1')]").isEmpty());
    }

    @Test
    void creatingWithADuplicateSkuIsRejectedWithConflict() throws Exception {
        String adminToken = tokenFor("admin@orderflow.dev", "ROLE_ADMIN");

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newProductRequest("DUP-1"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newProductRequest("DUP-1"))))
                .andExpect(status().isConflict());
    }

    @Test
    void searchFindsAProductByPartialCaseInsensitiveName() throws Exception {
        String adminToken = tokenFor("admin@orderflow.dev", "ROLE_ADMIN");
        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "Searchable Gadget", "sku", "SEARCH-1",
                                        "price", BigDecimal.TEN, "category", "Gadgets"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/products").param("name", "searchable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.sku == 'SEARCH-1')]").exists());
    }

    @Test
    void cachedProductReflectsUpdatesAfterEviction() throws Exception {
        String adminToken = tokenFor("admin@orderflow.dev", "ROLE_ADMIN");
        MvcResult createResult = mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newProductRequest("CACHE-1"))))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/id").asText();

        mockMvc.perform(get("/products/" + id)).andExpect(jsonPath("$.data.price").value(9.99));

        mockMvc.perform(put("/products/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "Widget", "sku", "CACHE-1",
                                        "price", BigDecimal.valueOf(14.50), "category", "Tools"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/products/" + id)).andExpect(jsonPath("$.data.price").value(14.50));
    }

    private String tokenFor(String email, String role) {
        return jwtService.generateAccessToken(email, UUID.randomUUID().toString(), List.of(new SimpleGrantedAuthority(role)));
    }

    private Map<String, Object> newProductRequest(String sku) {
        return Map.of("name", "Widget", "description", "A widget", "sku", sku,
                "price", BigDecimal.valueOf(9.99), "category", "Tools");
    }
}
