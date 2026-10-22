package com.wrda.infrastructure.idempotency.http;

import com.wrda.infrastructure.orders.contract.model.AtlasError;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Creates consistent API error responses for idempotency protocol failures. */
public final class IdempotencyProblemResponse {
    private IdempotencyProblemResponse() {
    }

    public static Response.ResponseBuilder create(int status, String code, String message) {
        AtlasError error = new AtlasError();
        error.setCode(code);
        error.setMessage(message);
        error.setCategory(AtlasError.CategoryEnum.IDEMPOTENCY);
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(error);
    }
}
