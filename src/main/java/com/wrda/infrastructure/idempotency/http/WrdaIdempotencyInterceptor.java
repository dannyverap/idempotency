package com.wrda.infrastructure.idempotency.http;

import com.wrda.application.idempotency.IdempotencyService;
import com.wrda.application.idempotency.model.AcquireResult;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.ws.rs.core.Response;

/** Orchestrates HTTP idempotency; collaborators own validation, request identity and replay details. */
@WrdaIdempotency
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 300)
public class WrdaIdempotencyInterceptor {
    private final RoutingContext routingContext;
    private final IdempotencyService service;
    private final IdempotencyKeyValidator keyValidator;
    private final IdempotencyRequestIdentityResolver identityResolver;
    private final IdempotencyResponseHandler responseHandler;
    private final TransactionSynchronizationRegistry transactionRegistry;

    @Inject
    public WrdaIdempotencyInterceptor(RoutingContext routingContext, IdempotencyService service,
            IdempotencyKeyValidator keyValidator,
            IdempotencyRequestIdentityResolver identityResolver,
            IdempotencyResponseHandler responseHandler,
            TransactionSynchronizationRegistry transactionRegistry) {
        this.routingContext = routingContext;
        this.service = service;
        this.keyValidator = keyValidator;
        this.identityResolver = identityResolver;
        this.responseHandler = responseHandler;
        this.transactionRegistry = transactionRegistry;
    }

    @AroundInvoke
    Object around(InvocationContext invocation) throws Exception {
        Response preconditionFailure = validateInvocation(invocation);
        if (preconditionFailure != null) {
            return preconditionFailure;
        }

        IdempotencyKeyValidator.Validation validation = keyValidator.validate(
                routingContext.request().headers().getAll(IdempotencyKeyValidator.HEADER_NAME));
        if (!validation.isValid()) {
            return IdempotencyProblemResponse.create(400, validation.errorCode(), validation.message()).build();
        }

        IdempotencyRequestIdentityResolver.RequestIdentity requestIdentity =
                identityResolver.resolve(validation.key(), invocation);
        AcquireResult acquisition = service.acquire(requestIdentity.storageKey(), requestIdentity.requestHash());
        return handleAcquisition(invocation, requestIdentity.storageKey(), acquisition);
    }

    private Response validateInvocation(InvocationContext invocation) {
        if (!Response.class.equals(invocation.getMethod().getReturnType())) {
            return IdempotencyProblemResponse.create(500, "IDEMPOTENCY_RESPONSE_TYPE_REQUIRED",
                    "Los endpoints idempotentes deben retornar jakarta.ws.rs.core.Response").build();
        }
        if (transactionRegistry.getTransactionStatus() != Status.STATUS_ACTIVE) {
            return IdempotencyProblemResponse.create(500, "IDEMPOTENCY_TRANSACTION_REQUIRED",
                    "El endpoint idempotente debe ejecutarse en una transacción JTA activa").build();
        }
        if (identityResolver.requiresAuthenticationButIsAnonymous()) {
            return IdempotencyProblemResponse.create(401, "IDEMPOTENCY_AUTHENTICATION_REQUIRED",
                    "La operación idempotente requiere una identidad autenticada").build();
        }
        return null;
    }

    private Object handleAcquisition(InvocationContext invocation, String storageKey,
            AcquireResult acquisition) throws Exception {
        if (acquisition instanceof AcquireResult.Acquired) {
            return responseHandler.execute(invocation, storageKey);
        }
        if (acquisition instanceof AcquireResult.Replay replay) {
            return responseHandler.replay(storageKey, replay.response());
        }
        if (acquisition instanceof AcquireResult.InProgress) {
            return IdempotencyProblemResponse.create(409, "REQUEST_IN_PROGRESS",
                    "La solicitud con esta Idempotency-Key continúa en proceso o requiere recuperación operativa.")
                    .header("Retry-After", "1").build();
        }
        if (acquisition instanceof AcquireResult.KeyReused) {
            return IdempotencyProblemResponse.create(422, "IDEMPOTENCY_KEY_REUSED",
                    "Esta Idempotency-Key ya se utilizó con una solicitud distinta.").build();
        }
        throw new IllegalStateException("Unknown idempotency acquisition result");
    }
}
