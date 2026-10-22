package com.wrda.infrastructure.idempotency.http;

import com.wrda.infrastructure.idempotency.security.IdempotencyHmac;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.InvocationContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Resolves caller scope and derives non-reversible storage identifiers for one HTTP request. */
@ApplicationScoped
public class IdempotencyRequestIdentityResolver {
    private final RoutingContext routingContext;
    private final SecurityIdentity securityIdentity;
    private final IdempotencyHmac hmac;
    private final RequestFingerprintBuilder fingerprintBuilder;

    @ConfigProperty(name = "wrda.idempotency.require-authenticated-identity", defaultValue = "false")
    boolean requireAuthenticatedIdentity;

    @ConfigProperty(name = "wrda.idempotency.tenant-identity-attribute", defaultValue = "tenant_id")
    String tenantIdentityAttribute;

    @Inject
    public IdempotencyRequestIdentityResolver(RoutingContext routingContext,
            SecurityIdentity securityIdentity, IdempotencyHmac hmac,
            RequestFingerprintBuilder fingerprintBuilder) {
        this.routingContext = routingContext;
        this.securityIdentity = securityIdentity;
        this.hmac = hmac;
        this.fingerprintBuilder = fingerprintBuilder;
    }

    public boolean requiresAuthenticationButIsAnonymous() {
        return requireAuthenticatedIdentity && securityIdentity.isAnonymous();
    }

    public RequestIdentity resolve(String rawKey, InvocationContext invocation) throws Exception {
        String principalScope = principalScope();
        String method = routingContext.request().method().name();
        String path = routingContext.request().path();
        String canonicalRequest = fingerprintBuilder.build(invocation, principalScope, method, path,
                routingContext.request().query());
        return new RequestIdentity(
                hmac.idempotencyKey(principalScope, method, path, rawKey),
                hmac.requestFingerprint(canonicalRequest));
    }

    private String principalScope() {
        String principal = securityIdentity.isAnonymous() || securityIdentity.getPrincipal() == null
                ? "anonymous" : securityIdentity.getPrincipal().getName();
        Object tenant = securityIdentity.getAttribute(tenantIdentityAttribute);
        return tenant == null ? principal : principal + "\n" + tenant;
    }

    public record RequestIdentity(String storageKey, String requestHash) {
    }
}
