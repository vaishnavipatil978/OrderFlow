package com.orderflow.notificationservice.controller;

import com.orderflow.common.response.ApiResponse;
import com.orderflow.notificationservice.dto.NotificationResponse;
import com.orderflow.notificationservice.service.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** Lists the caller's own notifications only; the recipient is taken from the token. */
    @GetMapping
    public ApiResponse<Page<NotificationResponse>> listMine(Authentication authentication, Pageable pageable) {
        return ApiResponse.success(notificationService.listForRecipient(authentication.getName(), pageable));
    }
}
