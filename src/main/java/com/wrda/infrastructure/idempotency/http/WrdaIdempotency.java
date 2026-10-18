package com.wrda.infrastructure.idempotency.http;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un metodo de resource JAX-RS como idempotente via header {@code Idempotency-Key}.
 * <p>
 * Requisito: el metodo debe retornar {@link jakarta.ws.rs.core.Response}.
 * Solo se guardan (y se re-envian) respuestas 200/201.
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface WrdaIdempotency {
}
