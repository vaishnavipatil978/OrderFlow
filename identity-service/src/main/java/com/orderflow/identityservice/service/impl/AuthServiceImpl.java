package com.orderflow.identityservice.service.impl;

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
import com.orderflow.common.security.JwtService;
import com.orderflow.identityservice.repository.UserRepository;
import com.orderflow.identityservice.security.UserPrincipal;
import com.orderflow.identityservice.service.interfaces.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_ROLE = "CUSTOMER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("An account with this email already exists");
        }
        Role defaultRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Default %s role not seeded".formatted(DEFAULT_ROLE)));

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.getRoles().add(defaultRole);
        userRepository.save(user);

        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException ex) {
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (!existing.isUsable()) {
            throw new UnauthorizedException("Refresh token expired or revoked");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);
        return issueTokens(existing.getUser());
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private AuthResponse issueTokens(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = jwtService.generateAccessToken(
                principal.getUsername(), principal.getUserId().toString(), principal.getAuthorities());
        String rawRefreshToken = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawRefreshToken));
        refreshToken.setExpiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()));
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.bearer(accessToken, rawRefreshToken, jwtService.getAccessTokenExpirationSeconds());
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
