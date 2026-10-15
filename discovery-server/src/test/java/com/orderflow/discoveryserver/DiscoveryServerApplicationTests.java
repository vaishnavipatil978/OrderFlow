package com.orderflow.discoveryserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DiscoveryServerApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the standalone Eureka server (no self-registration, no registry
        // fetch) and its security config wire together without error.
    }
}
