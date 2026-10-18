package com.wrda.infrastructure.idempotency.http;

import jakarta.ws.rs.core.Response;

/** Rebuilds one kind of successful response from persisted business resources. */
public interface IdempotencyReplayStrategy {

    String resourceType();

    Response replay(String resourceReference, int statusCode);
}
