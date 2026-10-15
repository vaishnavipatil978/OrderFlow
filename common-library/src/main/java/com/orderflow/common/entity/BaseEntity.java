package com.orderflow.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base type for every JPA entity across OrderFlow's business services. Provides
 * creation/modification timestamps (populated by Spring Data JPA auditing, see
 * {@link JpaAuditingAutoConfiguration}) and optimistic locking via {@code @Version}.
 *
 * {@code createdBy}/{@code updatedBy} are intentionally not included yet - that needs
 * an AuditorAware backed by the authenticated principal, which requires the JWT
 * security context that identity-service has not been built yet.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
