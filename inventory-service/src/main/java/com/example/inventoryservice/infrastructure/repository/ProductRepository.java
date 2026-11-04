package com.example.inventoryservice.infrastructure.repository;

import com.example.inventoryservice.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    // additional query methods can be added as needed
}

