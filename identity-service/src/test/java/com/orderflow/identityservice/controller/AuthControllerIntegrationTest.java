package com.orderflow.identityservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.identityservice.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the whole stack against a real Postgres (via AbstractIntegrationTest) and the
 * real Spring Security filter chain - not just each layer in isolation - so it's the one
 * place proving register -> login -> protected endpoint -> refresh -> logout and the
 * admin-only RBAC boundary actually work end to end, including the seed admin account
 * AdminBootstrapRunner creates on every context startup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerThenLoginThenAccessProtectedEndpoint() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "Jane", "Doe").andExpect(status().isCreated());

        JsonNode login = login(email, "password123");
        String accessToken = login.at("/data/accessToken").asText();
        assertThat(accessToken).isNotBlank();

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
    }

    @Test
    void duplicateRegistrationIsRejectedWithConflict() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "Jane", "Doe").andExpect(status().isCreated());

        register(email, "password123", "Jane", "Doe").andExpect(status().isConflict());
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "Jane", "Doe").andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new java.util.HashMap<>(java.util.Map.of("email", email, "password", "wrong-password")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotListAllUsersButAdminCan() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "Jane", "Doe").andExpect(status().isCreated());
        String customerToken = login(email, "password123").at("/data/accessToken").asText();

        mockMvc.perform(get("/users").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        String adminToken = login("admin@orderflow.dev", "ChangeMe123!").at("/data/accessToken").asText();
        mockMvc.perform(get("/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void refreshRotatesTokenAndInvalidatesThePrevious() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "Jane", "Doe").andExpect(status().isCreated());
        JsonNode login = login(email, "password123");
        String refreshToken = login.at("/data/refreshToken").asText();

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode refreshed = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        assertThat(refreshed.at("/data/refreshToken").asText()).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshTokenSoItCanNoLongerBeUsed() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "Jane", "Doe").andExpect(status().isCreated());
        String refreshToken = login(email, "password123").at("/data/refreshToken").asText();

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions register(
            String email, String password, String firstName, String lastName) throws Exception {
        return mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "email", email, "password", password, "firstName", firstName, "lastName", lastName))));
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String uniqueEmail() {
        return "user-" + java.util.UUID.randomUUID() + "@orderflow.dev";
    }
}
