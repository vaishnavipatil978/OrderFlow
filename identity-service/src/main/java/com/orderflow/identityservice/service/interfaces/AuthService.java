package com.orderflow.identityservice.service.interfaces;

import com.orderflow.identityservice.dto.AuthResponse;
import com.orderflow.identityservice.dto.LoginRequest;
import com.orderflow.identityservice.dto.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(String refreshToken);

    void logout(String refreshToken);
}
