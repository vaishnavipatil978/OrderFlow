package com.orderflow.identityservice.service.impl;

import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.identityservice.dto.UserResponse;
import com.orderflow.identityservice.mapper.UserMapper;
import com.orderflow.identityservice.repository.UserRepository;
import com.orderflow.identityservice.service.interfaces.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public UserResponse getCurrentUser(String email) {
        return userRepository.findByEmail(email)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(userMapper::toResponse).toList();
    }
}
