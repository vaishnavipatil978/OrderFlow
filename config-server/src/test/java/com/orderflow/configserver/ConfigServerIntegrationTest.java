package com.orderflow.configserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the native backend end to end: an unauthenticated request is rejected,
 * an authenticated request is served, and the served config actually matches what's
 * in config-repo/ - proving the server reads real files, not just that it boots.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerIntegrationTest {

    private static final String USERNAME = "config-client";
    private static final String PASSWORD = "config-client-secret";

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void healthEndpointIsAccessibleWithoutAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void configEndpointRequiresAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity("/identity-service/default", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void servesIdentityServicePortFromNativeBackend() throws IOException {
        ResponseEntity<String> response = restTemplate.withBasicAuth(USERNAME, PASSWORD)
                .getForEntity("/identity-service/default", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(findPropertyValue(response.getBody(), "server.port")).isEqualTo("8081");
    }

    @Test
    void sharedApplicationYamlIsLayeredUnderServiceSpecificConfig() throws IOException {
        ResponseEntity<String> response = restTemplate.withBasicAuth(USERNAME, PASSWORD)
                .getForEntity("/product-service/default", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(findPropertyValue(response.getBody(), "server.port")).isEqualTo("8082");
        assertThat(findPropertyValue(response.getBody(), "management.endpoints.web.exposure.include")).isEqualTo("health,info");
    }

    private String findPropertyValue(String responseBody, String propertyKey) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        for (JsonNode propertySource : root.get("propertySources")) {
            JsonNode value = propertySource.get("source").get(propertyKey);
            if (value != null) {
                return value.asText();
            }
        }
        return null;
    }
}
