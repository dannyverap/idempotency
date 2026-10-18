package com.wrda.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Order(String orderId, String customerId, BigDecimal amount, String status, Instant createdAt) {

    public static Order create(String orderId, String customerId, BigDecimal amount, Instant createdAt) {
        if (customerId == null || customerId.isBlank()
                || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderException();
        }
        return new Order(orderId, customerId, amount, "CREATED", createdAt);
    }
}
