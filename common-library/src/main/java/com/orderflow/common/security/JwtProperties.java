package com.orderflow.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from orderflow.jwt.* - shared by every service that validates JWTs (all of them,
 * potentially) and identity-service specifically, which is also the only issuer. Lives in
 * the shared config-repo/application.yml, not any one service's own file, since the signing
 * secret must be identical everywhere for HS256 verification to work at all.
 */
@ConfigurationProperties(prefix = "orderflow.jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpirationMs,
        long refreshTokenExpirationMs,
        String issuer
) {
}
