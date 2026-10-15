package com.orderflow.discoveryserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the registry itself is reachable/healthy without auth, but its REST API
 * (which discloses service topology) requires Basic Auth - the same shape as
 * ConfigServerIntegrationTest, applied to the registry instead of the config repo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServerSecurityIntegrationTest {

    private static final String USERNAME = "test-eureka-user";
    private static final String PASSWORD = "test-eureka-pass";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointIsAccessibleWithoutAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void registryEndpointRequiresAuthentication() {
        ResponseEntity<String> response = getAppsRegistry(restTemplate);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registryEndpointIsAccessibleWithCorrectCredentials() {
        ResponseEntity<String> response = getAppsRegistry(restTemplate.withBasicAuth(USERNAME, PASSWORD));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> getAppsRegistry(TestRestTemplate template) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        return template.exchange("/eureka/apps", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
