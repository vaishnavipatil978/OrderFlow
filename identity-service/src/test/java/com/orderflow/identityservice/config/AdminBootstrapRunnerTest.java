package com.orderflow.identityservice.config;

import com.orderflow.identityservice.entity.Role;
import com.orderflow.identityservice.repository.RoleRepository;
import com.orderflow.identityservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationArguments applicationArguments;

    private AdminBootstrapRunner runner() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(userRepository, roleRepository, passwordEncoder);
        ReflectionTestUtils.setField(runner, "adminEmail", "admin@orderflow.dev");
        ReflectionTestUtils.setField(runner, "adminPassword", "ChangeMe123!");
        return runner;
    }

    @Test
    void doesNothingWhenAdminAlreadyExists() {
        when(userRepository.existsByEmail("admin@orderflow.dev")).thenReturn(true);

        runner().run(applicationArguments);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createsAdminWithEncodedPasswordWhenMissing() {
        when(userRepository.existsByEmail("admin@orderflow.dev")).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(new Role("ADMIN")));
        when(passwordEncoder.encode("ChangeMe123!")).thenReturn("encoded-password");

        runner().run(applicationArguments);

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                user.getEmail().equals("admin@orderflow.dev")
                        && user.getPasswordHash().equals("encoded-password")
                        && user.getRoles().stream().anyMatch(role -> role.getName().equals("ADMIN"))));
    }

    @Test
    void failsFastIfAdminRoleWasNotSeeded() {
        when(userRepository.existsByEmail("admin@orderflow.dev")).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> runner().run(applicationArguments)).isInstanceOf(IllegalStateException.class);
    }
}
