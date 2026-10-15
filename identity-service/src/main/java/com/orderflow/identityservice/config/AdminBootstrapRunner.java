package com.orderflow.identityservice.config;

import com.orderflow.identityservice.entity.Role;
import com.orderflow.identityservice.entity.User;
import com.orderflow.identityservice.repository.RoleRepository;
import com.orderflow.identityservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every other roadmap item so far seeds reference/static data via Flyway (see V4__seed_roles.sql).
 * The first ADMIN account can't follow that pattern: its password needs PasswordEncoder.encode()
 * at whatever strength/algorithm is actually configured, which a SQL migration can't call - and
 * hand-computing a bcrypt hash to paste into a migration is exactly the kind of thing that quietly
 * breaks the next time the encoder strength changes. Idempotent: does nothing once an account with
 * this email already exists, so it's safe to run on every boot.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${orderflow.admin.email:admin@orderflow.dev}")
    private String adminEmail;

    @Value("${orderflow.admin.password:ChangeMe123!}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(adminEmail)) {
            return;
        }

        Role adminRole = roleRepository.findByName(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("Default %s role not seeded".formatted(ADMIN_ROLE)));

        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFirstName("Platform");
        admin.setLastName("Admin");
        admin.getRoles().add(adminRole);
        userRepository.save(admin);

        log.info("Seeded default admin account ({})", adminEmail);
    }
}
