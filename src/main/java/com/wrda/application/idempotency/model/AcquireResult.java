package com.wrda.application.idempotency.model;

/** Resultado de intentar "reclamar" una Idempotency-Key. */
public sealed interface AcquireResult {

    /** Eres el primero: ejecuta la logica de negocio. */
    record Acquired() implements AcquireResult {}

    /** La key ya termino con exito: devuelve la respuesta guardada. */
    record Replay(StoredResponse response) implements AcquireResult {}

    /** Otra peticion con la misma key sigue ejecutandose. */
    record InProgress() implements AcquireResult {}

    /** La key ya se uso con un request distinto. */
    record KeyReused() implements AcquireResult {}

    /** Foto inmutable de la respuesta guardada (no es una entidad JPA). */
    record StoredResponse(int statusCode, String body, String contentType, String location) {}
}
