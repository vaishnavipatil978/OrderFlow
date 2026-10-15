package com.orderflow.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Shared by every service that needs to verify a JWT (parseClaims) - only identity-service
 * actually calls generateAccessToken, since it's the platform's one token issuer, but the
 * signing/verification key setup is identical either way, so one class does both rather
 * than duplicating the key-construction logic between an "issuer" and a "verifier" type.
 */
public class JwtService {

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }

    public String generateAccessToken(String subject, String userId, Collection<? extends GrantedAuthority> authorities) {
        Instant now = Instant.now();
        List<String> roles = authorities.stream().map(GrantedAuthority::getAuthority).toList();

        return Jwts.builder()
                .subject(subject)
                .claim("userId", userId)
                .claim("roles", roles)
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(properties.accessTokenExpirationMs())))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getAccessTokenExpirationSeconds() {
        return properties.accessTokenExpirationMs() / 1000;
    }

    public long getRefreshTokenExpirationMs() {
        return properties.refreshTokenExpirationMs();
    }
}
