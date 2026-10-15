package com.orderflow.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ConfigServerApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the native config backend, security config, and Eureka-less
        // bootstrap all wire together without error.
    }
}
