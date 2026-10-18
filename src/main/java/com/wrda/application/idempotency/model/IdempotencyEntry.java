package com.wrda.application.idempotency.model;

/** Immutable view of a persisted idempotency key. */
public record IdempotencyEntry(
        String requestHash,
        IdempotencyStatus status,
        Integer statusCode,
        String responseBody,
        String contentType,
        String location,
        long createdAt,
        long updatedAt,
        long expiresAt) {
}
