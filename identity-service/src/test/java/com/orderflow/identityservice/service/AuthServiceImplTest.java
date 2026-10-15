package com.orderflow.identityservice.service;

import com.orderflow.common.exception.ConflictException;
import com.orderflow.common.exception.UnauthorizedException;
import com.orderflow.identityservice.dto.AuthResponse;
import com.orderflow.identityservice.dto.LoginRequest;
import com.orderflow.identityservice.dto.RegisterRequest;
import com.orderflow.identityservice.entity.RefreshToken;
import com.orderflow.identityservice.entity.Role;
import com.orderflow.identityservice.entity.User;
import com.orderflow.identityservice.repository.RefreshTokenRepository;
import com.orderflow.identityservice.repository.RoleRepository;
import com.orderflow.identityservice.repository.UserRepository;
import com.orderflow.common.security.JwtService;
import com.orderflow.identityservice.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository, roleRepository, refreshTokenRepository, passwordEncoder, authenticationManager, jwtService);
    }

    @Test
    void registerCreatesUserWithDefaultRoleAndIssuesTokens() {
        RegisterRequest request = new RegisterRequest("new@orderflow.dev", "password123", "New", "User");
        when(userRepository.existsByEmail("new@orderflow.dev")).thenReturn(false);
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(new Role("CUSTOMER")));
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn("access-token");
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.register(request);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getEmail()).isEqualTo("new@orderflow.dev");
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(savedUser.getValue().getRoles()).extracting(Role::getName).containsExactly("CUSTOMER");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("taken@orderflow.dev")).thenReturn(true);
        RegisterRequest request = new RegisterRequest("taken@orderflow.dev", "password123", "A", "B");

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(ConflictException.class);
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void registerFailsFastIfDefaultRoleWasNotSeeded() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.empty());
        RegisterRequest request = new RegisterRequest("new@orderflow.dev", "password123", "A", "B");

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loginRejectsBadCredentials() {
        doThrow(new BadCredentialsException("bad credentials"))
                .when(authenticationManager).authenticate(any());
        LoginRequest request = new LoginRequest("customer@orderflow.dev", "wrong-password");

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginIssuesTokensOnSuccess() {
        User user = userWithRole("customer@orderflow.dev", "CUSTOMER");
        when(userRepository.findByEmail("customer@orderflow.dev")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn("access-token");
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        LoginRequest request = new LoginRequest("customer@orderflow.dev", "correct-password");

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void refreshRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("some-raw-token")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshRejectsExpiredToken() {
        RefreshToken expired = refreshTokenFor(userWithRole("customer@orderflow.dev", "CUSTOMER"), Instant.now().minusSeconds(60), false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("some-raw-token")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshRejectsRevokedToken() {
        RefreshToken revoked = refreshTokenFor(userWithRole("customer@orderflow.dev", "CUSTOMER"), Instant.now().plusSeconds(60), true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh("some-raw-token")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshRotatesTokenOnSuccess() {
        RefreshToken valid = refreshTokenFor(userWithRole("customer@orderflow.dev", "CUSTOMER"), Instant.now().plusSeconds(60), false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(valid));
        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn("new-access-token");
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.refresh("some-raw-token");

        assertThat(valid.isRevoked()).isTrue();
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(any(RefreshToken.class));
    }

    @Test
    void logoutRevokesMatchingToken() {
        RefreshToken token = refreshTokenFor(userWithRole("customer@orderflow.dev", "CUSTOMER"), Instant.now().plusSeconds(60), false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        authService.logout("some-raw-token");

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void logoutIsNoOpForUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        authService.logout("unknown-token");

        verify(refreshTokenRepository, org.mockito.Mockito.never()).save(any());
    }

    private User userWithRole(String email, String roleName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("hashed");
        user.setFirstName("Test");
        user.setLastName("User");
        user.getRoles().add(new Role(roleName));
        return user;
    }

    private RefreshToken refreshTokenFor(User user, Instant expiresAt, boolean revoked) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash("irrelevant-hash");
        token.setExpiresAt(expiresAt);
        token.setRevoked(revoked);
        return token;
    }
}
