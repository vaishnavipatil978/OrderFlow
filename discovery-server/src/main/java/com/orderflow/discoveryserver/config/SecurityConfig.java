package com.orderflow.discoveryserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The Eureka dashboard and REST API disclose service topology (names, hosts, ports), so
 * everything except health/info requires HTTP Basic Auth. Eureka's HTTP client has no
 * separate username/password property - registering clients authenticate by embedding
 * credentials directly in eureka.client.service-url.defaultZone (see config-repo/application.yml).
 *
 * Credentials come from spring.security.user.name/password, sourced from EUREKA_USERNAME/
 * EUREKA_PASSWORD env vars with dev-only defaults - never rely on the defaults outside
 * local development.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
