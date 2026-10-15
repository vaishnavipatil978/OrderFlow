package com.orderflow.notificationservice.service.interfaces;

import com.orderflow.notificationservice.dto.NotificationResponse;
import com.orderflow.notificationservice.messaging.OrderEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    void handle(OrderEvent event);

    Page<NotificationResponse> listForRecipient(String recipient, Pageable pageable);
}
