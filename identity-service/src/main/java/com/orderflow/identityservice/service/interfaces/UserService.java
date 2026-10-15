package com.orderflow.identityservice.service.interfaces;

import com.orderflow.identityservice.dto.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse getCurrentUser(String email);

    List<UserResponse> getAllUsers();
}
