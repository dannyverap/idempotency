package com.wrda.infrastructure.idempotency.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrda.application.idempotency.IdempotencyService;
import com.wrda.application.idempotency.model.AcquireResult;
import com.wrda.infrastructure.idempotency.security.ResponseEncryption;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** Owns the lifecycle of successful response snapshots and safe replay reconstruction. */
@ApplicationScoped
public class IdempotencyResponseHandler {
    private static final Logger LOG = Logger.getLogger(IdempotencyResponseHandler.class);

    private final IdempotencyService service;
    private final ObjectMapper mapper;
    private final ResponseEncryption encryption;
    private final TransactionSynchronizationRegistry transactionRegistry;

    @ConfigProperty(name = "wrda.idempotency.max-response-bytes", defaultValue = "1048576")
    int maxResponseBytes;

    @Inject
    public IdempotencyResponseHandler(IdempotencyService service, ObjectMapper mapper,
            ResponseEncryption encryption, TransactionSynchronizationRegistry transactionRegistry) {
        this.service = service;
        this.mapper = mapper;
        this.encryption = encryption;
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

    public Response replay(String storageKey, AcquireResult.StoredResponse stored) {
        if (stored.encryptedResponse() == null || stored.encryptedResponse().isBlank()) {
            throw new IllegalStateException("Completed idempotency entry has no encrypted response");
        }
        try {
            byte[] plaintext = encryption.decrypt(storageKey, stored.encryptedResponse());
            ResponseSnapshot snapshot = mapper.readValue(plaintext, ResponseSnapshot.class);
            if (snapshot.status() != stored.statusCode()) {
                throw new IllegalStateException(
                        "Stored HTTP status does not match authenticated response snapshot");
            }
            Response original = snapshot.toResponse();
            return Response.fromResponse(original).header("Idempotency-Replayed", "true").build();
        } catch (Exception exception) {
            LOG.error("Could not authenticate or reconstruct idempotency response", exception);
            throw new IllegalStateException("Could not safely replay the stored response", exception);
        }
    }

    private void storeSuccessfulResponse(String storageKey, Response response, int status) throws Exception {
        ResponseSnapshot snapshot = ResponseSnapshot.capture(response, mapper);
        byte[] plaintext = mapper.writeValueAsBytes(snapshot);
        if (plaintext.length > maxResponseBytes) {
            throw new IllegalArgumentException("Idempotency response exceeds configured size limit");
        }
        String encrypted = encryption.encrypt(storageKey, plaintext);
        // REQUIRED joins the endpoint transaction so business rows and replay commit atomically.
        service.complete(storageKey, status, encrypted);
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
