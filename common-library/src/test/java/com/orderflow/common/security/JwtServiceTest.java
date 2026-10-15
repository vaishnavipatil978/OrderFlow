package com.orderflow.common.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "ZwxARLiyNc9Of8a4WsDzxQxaFYNhGHQf9SAsYSlX1Ik=";

    @Test
    void generatesTokenWhoseClaimsRoundTrip() {
        JwtService jwtService = jwtService(60_000);

        String token = jwtService.generateAccessToken(
                "customer@orderflow.dev", "user-id-123", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        var claims = jwtService.parseClaims(token);

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);

        assertThat(claims.getSubject()).isEqualTo("customer@orderflow.dev");
        assertThat(claims.get("userId", String.class)).isEqualTo("user-id-123");
        assertThat(roles).containsExactly("ROLE_CUSTOMER");
        assertThat(claims.getIssuer()).isEqualTo("orderflow-common-test");
    }

    @Test
    void expiredTokenFailsToParse() {
        JwtService jwtService = jwtService(-1_000);
        String token = jwtService.generateAccessToken(
                "customer@orderflow.dev", "user-id-123", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        assertThatThrownBy(() -> jwtService.parseClaims(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithADifferentKeyIsRejected() {
        JwtService issuer = jwtService(60_000);
        JwtService verifier = new JwtService(new JwtProperties(
                "sU7B2EbYpaqiqnskGtgFTzODDm3CvW3nzElWuENIMGg=",
                60_000, 604_800_000, "orderflow-common-test"));
        String token = issuer.generateAccessToken(
                "customer@orderflow.dev", "user-id-123", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        assertThatThrownBy(() -> verifier.parseClaims(token)).isInstanceOf(SignatureException.class);
    }

    private JwtService jwtService(long accessTokenExpirationMs) {
        return new JwtService(new JwtProperties(SECRET, accessTokenExpirationMs, 604_800_000, "orderflow-common-test"));
    }
}
