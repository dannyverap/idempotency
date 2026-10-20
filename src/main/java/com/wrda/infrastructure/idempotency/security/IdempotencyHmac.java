package com.wrda.infrastructure.idempotency.security;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;

/** Domain-separated HMACs for key lookup and request fingerprints. */
@ApplicationScoped
public class IdempotencyHmac {
    @ConfigProperty(name = "wrda.idempotency.hmac-key")
    String configuredKey;

    private byte[] key;

    @PostConstruct
    void initialize() {
        try {
            key = Base64.getDecoder().decode(configuredKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid Base64 idempotency HMAC key", exception);
        }
        if (key.length < 32) {
            throw new IllegalStateException("The idempotency HMAC key must contain at least 256 bits");
        }
    }

    public String idempotencyKey(String principalScope, String method, String path, String rawKey) {
        return hmac("idempotency-key-v1\n" + principalScope + "\n" + method + "\n" + path + "\n" + rawKey);
    }

    public String requestFingerprint(String canonicalRequest) {
        return hmac("request-fingerprint-v1\n" + canonicalRequest);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not calculate idempotency HMAC", exception);
        }
    }
}
