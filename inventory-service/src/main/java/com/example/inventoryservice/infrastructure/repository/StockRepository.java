package com.example.inventoryservice.infrastructure.repository;

import com.example.inventoryservice.domain.Stock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Repository for Stock with custom atomic update queries.
 */
public interface StockRepository extends CrudRepository<Stock, UUID> {

    /**
     * Atomically decrement available and increment reserved for a product.
     * Returns the number of rows affected (0 if insufficient stock).
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE stock SET available = available - :qty, reserved = reserved + :qty, updated_at = now() " +
            "WHERE product_id = :productId AND available >= :qty", nativeQuery = true)
    int decrementAvailableAndIncrementReserved(@Param("productId") UUID productId, @Param("qty") int qty);

    /**
     * Reverse the reservation – increment available and decrement reserved.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE stock SET available = available + :qty, reserved = reserved - :qty, updated_at = now() " +
            "WHERE product_id = :productId", nativeQuery = true)
    int incrementAvailableAndDecrementReserved(@Param("productId") UUID productId, @Param("qty") int qty);
}
