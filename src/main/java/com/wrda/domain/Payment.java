package com.wrda.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Payment(String paymentId, String orderId, BigDecimal amount, String status,
                      Instant createdAt) {

    public static Payment create(String paymentId, String orderId, BigDecimal amount,
                                 Instant createdAt) {
        if (orderId == null || orderId.isBlank() || amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentException();
        }
        return new Payment(paymentId, orderId, amount, "SUCCEEDED", createdAt);
    }
}
