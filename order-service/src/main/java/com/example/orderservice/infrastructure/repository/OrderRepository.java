package com.example.orderservice.infrastructure.repository;

import com.example.orderservice.domain.Order;
import com.example.orderservice.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT o FROM Order o WHERE o.status IN (:statuses) AND o.updatedAt < :deadline")
    List<Order> findStaleOrders(@Param("statuses") Collection<OrderStatus> statuses, @Param("deadline") Instant deadline);

    default List<Order> findStaleOrders(Instant deadline) {
        return findStaleOrders(List.of(OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT), deadline);
    }
}

