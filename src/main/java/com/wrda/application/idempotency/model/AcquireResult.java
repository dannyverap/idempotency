package com.wrda.application.idempotency.model;

/** Resultado de intentar "reclamar" una Idempotency-Key. */
public sealed interface AcquireResult {

    /** Eres el primero: ejecuta la logica de negocio. */
    record Acquired() implements AcquireResult {}

    /** La key ya termino con exito: recupera el recurso guardado para reconstruir la respuesta. */
    record Replay(StoredResponse response) implements AcquireResult {}

    /** Otra peticion con la misma key sigue ejecutandose. */
    record InProgress() implements AcquireResult {}

    /** La key ya se uso con un request distinto. */
    record KeyReused() implements AcquireResult {}

    /** Referencia minima necesaria para reconstruir una respuesta exitosa. */
    record StoredResponse(int statusCode, String resourceReference) {}
}
