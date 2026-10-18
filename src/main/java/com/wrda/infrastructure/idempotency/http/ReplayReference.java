package com.wrda.infrastructure.idempotency.http;

/** Minimal, non-response data needed to locate the result of a successful operation. */
public record ReplayReference(String resourceType, String value) {
}
