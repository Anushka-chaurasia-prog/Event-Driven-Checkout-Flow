package com.example.common.util;

import java.util.UUID;

/**
 * Utility to generate correlation IDs for out‑of‑process messaging.
 * The same format is used across all services.
 */
public final class CorrelationIdGenerator {
    private CorrelationIdGenerator() {}

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}

