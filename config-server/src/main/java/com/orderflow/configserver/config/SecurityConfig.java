package com.orderflow.configserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Config Server carries other services' configuration - potentially including secrets -
 * so its environment endpoints require HTTP Basic Auth. Health/info are left open so
 * container/orchestrator health probes don't need credentials.
 *
 * Credentials come from spring.security.user.name/password (see application.yml), which
 * are themselves sourced from CONFIG_SERVER_USERNAME/CONFIG_SERVER_PASSWORD env vars with
 * dev-only defaults - never rely on the defaults outside local development.
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
