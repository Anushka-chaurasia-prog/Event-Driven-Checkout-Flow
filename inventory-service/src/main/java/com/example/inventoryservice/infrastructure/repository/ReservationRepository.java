package com.example.inventoryservice.infrastructure.repository;

import com.example.inventoryservice.domain.Reservation;
import com.example.inventoryservice.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /** Does a reservation already exist for the given order? */
    boolean existsByOrderId(UUID orderId);

    /** Find the reservation for a specific order. */
    Optional<Reservation> findByOrderId(UUID orderId);

    /** Find all reservations that are RESERVED and have expired (TTL passed). */
    @Query("SELECT r FROM Reservation r WHERE r.status = :status AND r.expiresAt < :now")
    List<Reservation> findByStatusAndExpiresAtBefore(@Param("status") ReservationStatus status,
                                                      @Param("now") Instant now);
}

