package com.orderflow.identityservice.entity;

import com.orderflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Plain name, no "ROLE_" prefix stored (e.g. "CUSTOMER", "ADMIN") - the prefix Spring
 * Security's hasRole()/hasAuthority() convention expects is added when authorities are
 * built (see UserPrincipal), not persisted.
 */
@Getter
@Setter
@Entity
@Table(name = "roles")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false, of = "name")
public class Role extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    public Role(String name) {
        this.name = name;
    }
}
