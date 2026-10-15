package com.orderflow.orderservice.entity;

import com.orderflow.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor
public class CustomerOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_email", nullable = false, updatable = false)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    public CustomerOrder(String customerEmail, BigDecimal totalAmount) {
        this.customerEmail = customerEmail;
        this.totalAmount = totalAmount;
        this.status = OrderStatus.PENDING;
    }

    public void addItem(UUID productId, BigDecimal unitPrice, int quantity) {
        items.add(new OrderItem(this, productId, unitPrice, quantity));
    }

    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
        this.failureReason = null;
    }

    public void cancel(String reason) {
        this.status = OrderStatus.CANCELLED;
        this.failureReason = reason;
    }
}
