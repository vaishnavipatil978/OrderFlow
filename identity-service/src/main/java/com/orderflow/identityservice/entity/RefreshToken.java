package com.orderflow.identityservice.entity;

import com.orderflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Only tokenHash is ever persisted - the raw refresh token is returned to the client once
 * (at login/refresh time) and never stored, so a database compromise doesn't hand over
 * usable refresh tokens the way storing them in plaintext would.
 */
@Getter
@Setter
@Entity
@Table(name = "refresh_tokens")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false, of = "tokenHash")
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    public boolean isUsable() {
        return !revoked && expiresAt.isAfter(Instant.now());
    }
}
