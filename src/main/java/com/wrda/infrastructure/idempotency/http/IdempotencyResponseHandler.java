package com.wrda.infrastructure.idempotency.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrda.application.idempotency.IdempotencyService;
import com.wrda.application.idempotency.model.AcquireResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** Executes successful endpoints once and stores/reconstructs their HTTP response snapshot. */
@ApplicationScoped
public class IdempotencyResponseHandler {
    private static final Logger LOG = Logger.getLogger(IdempotencyResponseHandler.class);

    private final IdempotencyService service;
    private final ObjectMapper mapper;
    private final TransactionSynchronizationRegistry transactionRegistry;

    @ConfigProperty(name = "wrda.idempotency.max-response-bytes", defaultValue = "1048576")
    int maxResponseBytes;

    @Inject
    public IdempotencyResponseHandler(IdempotencyService service, ObjectMapper mapper,
            TransactionSynchronizationRegistry transactionRegistry) {
        this.service = service;
        this.mapper = mapper;
        this.transactionRegistry = transactionRegistry;
    }

    public Object execute(InvocationContext invocation, String storageKey) throws Exception {
        try {
            Object result = invocation.proceed();
            if (result == null) {
                service.release(storageKey);
                return null;
            }

            Response response = (Response) result;
            int status = response.getStatus();
            if (isSuccessful(status)) {
                storeSuccessfulResponse(storageKey, response, status);
            } else {
                transactionRegistry.setRollbackOnly();
                service.release(storageKey);
            }
            return result;
        } catch (Exception | Error failure) {
            releaseQuietly(storageKey);
            throw failure;
        }
    }

    public Response replay(AcquireResult.StoredResponse stored) {
        if (stored.responseSnapshot() == null || stored.responseSnapshot().isBlank()) {
            throw new IllegalStateException("Completed idempotency entry has no response snapshot");
        }
        try {
            ResponseSnapshot snapshot = mapper.readValue(stored.responseSnapshot(), ResponseSnapshot.class);
            if (snapshot.status() != stored.statusCode()) {
                throw new IllegalStateException("Stored HTTP status does not match response snapshot");
            }
            Response original = snapshot.toResponse();
            return Response.fromResponse(original).header("Idempotency-Replayed", "true").build();
        } catch (Exception exception) {
            LOG.error("Could not deserialize or reconstruct idempotency response snapshot", exception);
            throw new IllegalStateException("Could not safely replay the stored response", exception);
        }
    }

    private void storeSuccessfulResponse(String storageKey, Response response, int status) throws Exception {
        ResponseSnapshot snapshot = ResponseSnapshot.capture(response, mapper);
        String serializedSnapshot = mapper.writeValueAsString(snapshot);
        int serializedBytes = serializedSnapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (serializedBytes > maxResponseBytes) {
            throw new IllegalArgumentException("Idempotency response exceeds configured size limit");
        }
        // REQUIRED joins the endpoint transaction so business rows and the snapshot commit atomically.
        service.complete(storageKey, status, serializedSnapshot);
    }

    private void releaseQuietly(String storageKey) {
        try {
            service.release(storageKey);
        } catch (RuntimeException releaseFailure) {
            LOG.error("Could not release idempotency reservation after failure", releaseFailure);
        }
    }

    private static boolean isSuccessful(int status) {
        return status >= 200 && status < 300;
    }
}
