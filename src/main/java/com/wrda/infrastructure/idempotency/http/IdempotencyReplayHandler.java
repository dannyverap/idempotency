package com.wrda.infrastructure.idempotency.http;

import jakarta.ws.rs.core.Response;

/** Extracts stable resource identifiers from a successful response. */
public interface IdempotencyReplayHandler {

    ReplayReference resourceReference(Response successfulResponse);
}
