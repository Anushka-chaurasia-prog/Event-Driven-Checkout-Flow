package com.example.orderservice.domain;

import com.example.common.util.CorrelationIdGenerator;
import com.example.orderservice.application.commands.ChargePaymentCmd;
import com.example.orderservice.application.commands.OrderCommand;
import com.example.orderservice.application.commands.PublishOrderConfirmedCmd;
import com.example.orderservice.application.commands.RefundPaymentCmd;
import com.example.orderservice.application.commands.ReleaseStockCmd;
import com.example.orderservice.application.commands.ReserveStockCmd;
import com.example.orderservice.domain.events.StockRejectedEvent;
import com.example.orderservice.domain.events.StockReleasedEvent;
import com.example.orderservice.domain.events.StockReservedEvent;
import com.example.orderservice.domain.events.PaymentFailedEvent;
import com.example.orderservice.domain.events.PaymentSucceededEvent;


import jakarta.persistence.*;
import java.util.*;
import java.time.Instant;

/**
 * Order aggregate root.
 */
@Entity
@Table(name = "orders")
public class Order {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "total_cents")
    private Long totalCents;

    @Column(name = "currency", nullable = false)
    private String currency = "INR";

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public Order(UUID id, UUID userId, String idempotencyKey) {
        this.id = id;
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.status = OrderStatus.PENDING;
    }

    // ----- Getters / Setters -----
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public Long getTotalCents() { return totalCents; }
    public void setTotalCents(Long totalCents) { this.totalCents = totalCents; }
    public String getCurrency() { return currency; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public List<OrderItem> getItems() { return items; }
    public void addItem(OrderItem item) { this.items.add(item); item.setOrder(this); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    // ----- State‑machine transition methods -----
    public Optional<OrderCommand> onStockReserved(StockReservedEvent e) {
        if (status != OrderStatus.PENDING) return Optional.empty();
        // fill unit prices & total
        e.getItems().forEach(line -> {
            Optional<OrderItem> oi = items.stream()
                    .filter(i -> i.getProductId().equals(line.getProductId()))
                    .findFirst();
            oi.ifPresent(item -> item.setUnitPriceCents(line.getUnitPriceCents()));
        });
        this.totalCents = e.getTotalCents();
        this.status = OrderStatus.AWAITING_PAYMENT;
        return Optional.of(new ChargePaymentCmd(id, userId, totalCents, currency));
    }

    public Optional<OrderCommand> onPaymentSucceeded(PaymentSucceededEvent e) {
        return switch (status) {
            case AWAITING_PAYMENT -> {
                this.status = OrderStatus.CONFIRMED;
                yield Optional.of(new PublishOrderConfirmedCmd(id));
            }
            case CANCELLING, CANCELLED -> {
                yield Optional.of(new RefundPaymentCmd(id, e.getPaymentId()));
            }
            default -> Optional.empty();
        };
    }

    public Optional<OrderCommand> onPaymentFailed(PaymentFailedEvent e) {
        if (status != OrderStatus.AWAITING_PAYMENT) return Optional.empty();
        this.status = OrderStatus.CANCELLING;
        this.failureReason = e.getReason();
        return Optional.of(new ReleaseStockCmd(id, "PAYMENT_FAILED"));
    }

    public Optional<OrderCommand> onStockRejected(StockRejectedEvent e) {
        if (status != OrderStatus.PENDING) return Optional.empty();
        this.status = OrderStatus.REJECTED;
        this.failureReason = e.getReason();
        return Optional.empty(); // no further command
    }

    public Optional<OrderCommand> onStockReleased(StockReleasedEvent e) {
        if (status != OrderStatus.CANCELLING) return Optional.empty();
        this.status = OrderStatus.CANCELLED;
        return Optional.empty();
    }

    public Optional<OrderCommand> onTimeout() {
        if (status == OrderStatus.PENDING || status == OrderStatus.AWAITING_PAYMENT) {
            this.status = OrderStatus.CANCELLING;
            this.failureReason = "TIMEOUT";
            return Optional.of(new ReleaseStockCmd(id, "TIMEOUT"));
        }
        return Optional.empty();
    }

    public Optional<OrderCommand> onCancelRequested() {
        if (status == OrderStatus.PENDING || status == OrderStatus.AWAITING_PAYMENT) {
            this.status = OrderStatus.CANCELLING;
            this.failureReason = "CUSTOMER_CANCEL";
            return Optional.of(new ReleaseStockCmd(id, "CUSTOMER_CANCEL"));
        }
        return Optional.empty();
    }
}

