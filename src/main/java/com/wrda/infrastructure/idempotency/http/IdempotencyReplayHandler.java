package com.wrda.infrastructure.idempotency.http;

import jakarta.ws.rs.core.Response;

/** Rebuilds the original successful HTTP response from a persisted resource reference. */
public interface IdempotencyReplayHandler {

    String resourceReference(Response successfulResponse);

    Response replay(String resourceReference, int statusCode);
}
