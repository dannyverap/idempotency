package com.wrda.application.idempotency.model;

/** Immutable view of persisted idempotency state. */
public record IdempotencyEntry(
        String requestHash,
        IdempotencyStatus status,
        Integer statusCode,
        String encryptedResponse,
        long createdAt,
        long updatedAt,
        long expiresAt) {
}
