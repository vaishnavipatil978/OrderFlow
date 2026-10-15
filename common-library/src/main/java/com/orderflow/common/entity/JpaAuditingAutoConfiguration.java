package com.orderflow.common.entity;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Gated on an actual EntityManagerFactory *bean*, not just the JPA classes being on the
 * classpath - a service can carry spring-boot-starter-data-jpa as a dependency (per the
 * intended tech stack) without yet having a real datasource or any entities. Enabling
 * @EnableJpaAuditing in that state fails hard ("JPA metamodel must not be empty"), so this
 * only activates once JPA auto-configuration has actually produced a working EntityManagerFactory.
 */
@AutoConfiguration
@AutoConfigureAfter(HibernateJpaAutoConfiguration.class)
@ConditionalOnBean(EntityManagerFactory.class)
@EnableJpaAuditing
public class JpaAuditingAutoConfiguration {
}
