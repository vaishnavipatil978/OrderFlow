package com.orderflow.identityservice;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * One container shared across every test class that extends this (Testcontainers reuses it
 * for the whole JVM via the static field + @Container, not per test class) - real Postgres,
 * real Flyway migrations, real JPA mappings, not an in-memory stand-in that could mask a
 * dialect-specific bug the way H2 sometimes does.
 */
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orderflow_identity_test")
            .withUsername("orderflow")
            .withPassword("orderflow");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
