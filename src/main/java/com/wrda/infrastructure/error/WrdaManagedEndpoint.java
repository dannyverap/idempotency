package com.wrda.infrastructure.error;

import com.wrda.infrastructure.idempotency.http.WrdaIdempotency;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@InterceptorBinding
@WrdaIdempotency
@WrdaErrorManagement
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface WrdaManagedEndpoint {
}
