package com.wrda.infrastructure.idempotency.http;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un metodo de resource JAX-RS como idempotente via header {@code Idempotency-Key}.
 * <p>
 * Requiere que el método retorne {@link jakarta.ws.rs.core.Response} y se ejecute dentro
 * de una transacción JTA activa. Las respuestas 2xx JSON/texto se almacenan cifradas
 * y se reproducen sin ejecutar otra vez el método de negocio.
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface WrdaIdempotency {
}
