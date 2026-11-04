package com.example.inventoryservice.config;

import com.example.inventoryservice.domain.Product;
import com.example.inventoryservice.domain.Stock;
import com.example.inventoryservice.infrastructure.repository.ProductRepository;
import com.example.inventoryservice.infrastructure.repository.StockRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class DataInitializer {
    public static final UUID DEFAULT_PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Bean
    public CommandLineRunner initData(ProductRepository productRepo, StockRepository stockRepo) {
        return args -> {
            if (!productRepo.existsById(DEFAULT_PRODUCT_ID)) {
                Product p = new Product(DEFAULT_PRODUCT_ID, "PROD-1", "Demo Product", 5000L, "INR");
                productRepo.save(p);
                Stock s = new Stock(DEFAULT_PRODUCT_ID, 100);
                stockRepo.save(s);
            }
        };
    }
}

