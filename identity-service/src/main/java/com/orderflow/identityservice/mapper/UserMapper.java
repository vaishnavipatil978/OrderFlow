package com.orderflow.identityservice.mapper;

import com.orderflow.identityservice.dto.UserResponse;
import com.orderflow.identityservice.entity.Role;
import com.orderflow.identityservice.entity.User;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                roleNames(user.getRoles()),
                user.getCreatedAt());
    }

    private Set<String> roleNames(Set<Role> roles) {
        return roles.stream().map(Role::getName).collect(Collectors.toSet());
    }
}
