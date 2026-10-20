package com.wrda.infrastructure.idempotency.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrda.application.idempotency.IdempotencyService;
import com.wrda.application.idempotency.model.AcquireResult;
import com.wrda.infrastructure.idempotency.security.IdempotencyHmac;
import com.wrda.infrastructure.idempotency.security.ResponseEncryption;
import com.wrda.infrastructure.orders.contract.model.AtlasError;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reserves the key, captures the HTTP result, and persists only authenticated ciphertext. */
@WrdaIdempotency
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 300)
public class WrdaIdempotencyInterceptor {
    static final String HEADER = "Idempotency-Key";
    static final int MAX_KEY_LENGTH = 200;
    private static final Logger LOG = Logger.getLogger(WrdaIdempotencyInterceptor.class);

    private final RoutingContext routingContext;
    private final IdempotencyService service;
    private final ObjectMapper mapper;
    private final ResponseEncryption encryption;
    private final IdempotencyHmac hmac;
    private final SecurityIdentity identity;
    private final TransactionSynchronizationRegistry transactionRegistry;

    @ConfigProperty(name = "wrda.idempotency.require-authenticated-identity", defaultValue = "false")
    boolean requireAuthenticatedIdentity;

    @ConfigProperty(name = "wrda.idempotency.max-response-bytes", defaultValue = "1048576")
    int maxResponseBytes;

    @ConfigProperty(name = "wrda.idempotency.tenant-identity-attribute", defaultValue = "tenant_id")
    String tenantIdentityAttribute;

    @Inject
    public WrdaIdempotencyInterceptor(RoutingContext routingContext, IdempotencyService service,
            ObjectMapper mapper, ResponseEncryption encryption, IdempotencyHmac hmac,
            SecurityIdentity identity, TransactionSynchronizationRegistry transactionRegistry) {
        this.routingContext = routingContext;
        this.service = service;
        this.mapper = mapper;
        this.encryption = encryption;
        this.hmac = hmac;
        this.identity = identity;
        this.transactionRegistry = transactionRegistry;
    }

    @AroundInvoke
    Object around(InvocationContext context) throws Exception {
        if (!Response.class.equals(context.getMethod().getReturnType())) {
            return problem(500, "IDEMPOTENCY_RESPONSE_TYPE_REQUIRED",
                    "Los endpoints idempotentes deben retornar jakarta.ws.rs.core.Response").build();
        }
        if (transactionRegistry.getTransactionStatus() != Status.STATUS_ACTIVE) {
            return problem(500, "IDEMPOTENCY_TRANSACTION_REQUIRED",
                    "El endpoint idempotente debe ejecutarse en una transacción JTA activa").build();
        }
        if (requireAuthenticatedIdentity && identity.isAnonymous()) {
            return problem(401, "IDEMPOTENCY_AUTHENTICATION_REQUIRED",
                    "La operación idempotente requiere una identidad autenticada").build();
        }

        List<String> keyValues = routingContext.request().headers().getAll(HEADER);
        if (keyValues.size() != 1) {
            return problem(400, "IDEMPOTENCY_KEY_REQUIRED", "Debe enviarse exactamente un header Idempotency-Key").build();
        }
        String rawKey = keyValues.get(0);
        if (rawKey == null || rawKey.isBlank() || rawKey.length() > MAX_KEY_LENGTH
                || rawKey.chars().anyMatch(Character::isISOControl)) {
            return problem(400, "IDEMPOTENCY_KEY_INVALID", "Idempotency-Key está vacía, es demasiado larga o contiene caracteres de control").build();
        }

        String principalScope = principalScope();
        String method = routingContext.request().method().name();
        String path = routingContext.request().path();
        String storedKey = hmac.idempotencyKey(principalScope, method, path, rawKey);
        String requestHash = hmac.requestFingerprint(fingerprint(context, principalScope, method, path));
        AcquireResult acquired = service.acquire(storedKey, requestHash);

        if (acquired instanceof AcquireResult.Acquired) {
            return execute(context, storedKey);
        }
        if (acquired instanceof AcquireResult.Replay replay) {
            return replay(storedKey, replay.response());
        }
        if (acquired instanceof AcquireResult.InProgress) {
            return problem(409, "REQUEST_IN_PROGRESS",
                    "La solicitud con esta Idempotency-Key continúa en proceso o requiere recuperación operativa.")
                    .header("Retry-After", "1").build();
        }
        if (acquired instanceof AcquireResult.KeyReused) {
            return problem(422, "IDEMPOTENCY_KEY_REUSED",
                    "Esta Idempotency-Key ya se utilizó con una solicitud distinta.").build();
        }
        throw new IllegalStateException("Unknown idempotency acquisition result");
    }

    private Object execute(InvocationContext context, String storedKey) throws Exception {
        try {
            Object result = context.proceed();
            if (result == null) {
                service.release(storedKey);
                return null;
            }
            Response response = (Response) result;
            int status = response.getStatus();
            if (status >= 200 && status < 300) {
                ResponseSnapshot snapshot = ResponseSnapshot.capture(response, mapper);
                byte[] plaintext = mapper.writeValueAsBytes(snapshot);
                if (plaintext.length > maxResponseBytes) {
                    throw new IllegalArgumentException("Idempotency response exceeds configured size limit");
                }
                String encrypted = encryption.encrypt(storedKey, plaintext);
                // REQUIRED joins the endpoint transaction so business rows and replay commit atomically.
                service.complete(storedKey, status, encrypted);
            } else {
                transactionRegistry.setRollbackOnly();
                service.release(storedKey);
            }
            return result;
        } catch (Exception | Error failure) {
            releaseQuietly(storedKey);
            throw failure;
        }
    }

    private Response replay(String storedKey, AcquireResult.StoredResponse stored) {
        if (stored.encryptedResponse() == null || stored.encryptedResponse().isBlank()) {
            throw new IllegalStateException("Completed idempotency entry has no encrypted response");
        }
        try {
            byte[] plaintext = encryption.decrypt(storedKey, stored.encryptedResponse());
            ResponseSnapshot snapshot = mapper.readValue(plaintext, ResponseSnapshot.class);
            if (snapshot.status() != stored.statusCode()) {
                throw new IllegalStateException("Stored HTTP status does not match authenticated response snapshot");
            }
            Response original = snapshot.toResponse();
            return Response.fromResponse(original).header("Idempotency-Replayed", "true").build();
        } catch (Exception exception) {
            LOG.error("Could not authenticate or reconstruct idempotency response", exception);
            throw new IllegalStateException("Could not safely replay the stored response", exception);
        }
    }

    private String principalScope() {
        String principal = identity.isAnonymous() || identity.getPrincipal() == null
                ? "anonymous" : identity.getPrincipal().getName();
        Object tenant = identity.getAttribute(tenantIdentityAttribute);
        return tenant == null ? principal : principal + "\n" + tenant;
    }

    /** Stable method/path/query and JSON-serialized endpoint arguments, excluding the key itself. */
    private String fingerprint(InvocationContext context, String principalScope, String method, String path)
            throws Exception {
        Object[] parameters = context.getParameters();
        var methodParameters = context.getMethod().getParameters();
        List<Object> requestValues = new ArrayList<>(parameters.length);
        for (int i = 0; i < parameters.length; i++) {
            HeaderParam header = methodParameters[i].getAnnotation(HeaderParam.class);
            if (header == null || !HEADER.equalsIgnoreCase(header.value())) {
                requestValues.add(parameters[i]);
            }
        }
        String query = routingContext.request().query();
        byte[] serialized = mapper.writeValueAsBytes(requestValues);
        return principalScope + "\n" + method + "\n" + path + "\n" + (query == null ? "" : query)
                + "\n" + new String(serialized, StandardCharsets.UTF_8);
    }

    private void releaseQuietly(String key) {
        try {
            service.release(key);
        } catch (RuntimeException releaseFailure) {
            LOG.error("Could not release idempotency reservation after failure", releaseFailure);
        }
    }

    private static Response.ResponseBuilder problem(int status, String code, String message) {
        AtlasError error = new AtlasError();
        error.setCode(code);
        error.setMessage(message);
        error.setCategory(AtlasError.CategoryEnum.IDEMPOTENCY);
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(error);
    }
}
