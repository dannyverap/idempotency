package com.wrda.infrastructure.idempotency.http;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un metodo de resource JAX-RS como idempotente via header {@code Idempotency-Key}.
 * <p>
 * Requisitos: el metodo retorna {@link jakarta.ws.rs.core.Response} y su resource
 * implementa {@link IdempotencyReplayHandler}. Solo se guardan respuestas 200/201;
 * se conserva una referencia al recurso para reconstruir el replay.
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface WrdaIdempotency {
}
