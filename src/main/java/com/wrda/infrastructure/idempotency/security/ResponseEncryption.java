package com.wrda.infrastructure.idempotency.security;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** AES-256-GCM envelope encryption. Keys are supplied by the deployment secret manager. */
@ApplicationScoped
public class ResponseEncryption {
    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    @ConfigProperty(name = "wrda.idempotency.encryption-keys")
    String configuredKeys;

    @ConfigProperty(name = "wrda.idempotency.encryption-current-key-id")
    String currentKeyId;

    private Map<String, byte[]> keys;

    @PostConstruct
    void initialize() {
        Map<String, byte[]> parsed = new LinkedHashMap<>();
        for (String pair : configuredKeys.split(",")) {
            int separator = pair.indexOf(':');
            if (separator <= 0 || separator == pair.length() - 1) {
                throw new IllegalStateException("Invalid idempotency encryption key-ring configuration");
            }
            String id = pair.substring(0, separator).trim();
            byte[] key;
            try {
                key = Base64.getDecoder().decode(pair.substring(separator + 1).trim());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid Base64 idempotency encryption key", exception);
            }
            if (id.isBlank() || key.length != 32 || parsed.putIfAbsent(id, key) != null) {
                throw new IllegalStateException("Encryption keys must have unique IDs and be 256-bit keys");
            }
        }
        if (parsed.isEmpty() || !parsed.containsKey(currentKeyId)) {
            throw new IllegalStateException("Current idempotency encryption key ID is absent from the key ring");
        }
        keys = Map.copyOf(parsed);
    }

    /** Returns Base64(version || key-id-length || key-id || nonce || ciphertext-and-tag). */
    public String encrypt(String idempotencyKey, byte[] plaintext) {
        try {
            byte[] keyId = currentKeyId.getBytes(StandardCharsets.UTF_8);
            if (keyId.length > 255) {
                throw new IllegalStateException("Encryption key ID is too long");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys.get(currentKeyId), "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(idempotencyKey.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext);
            ByteBuffer envelope = ByteBuffer.allocate(2 + keyId.length + nonce.length + ciphertext.length);
            envelope.put(FORMAT_VERSION).put((byte) keyId.length).put(keyId).put(nonce).put(ciphertext);
            return Base64.getEncoder().encodeToString(envelope.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt idempotency response", exception);
        }
    }

    public byte[] decrypt(String idempotencyKey, String encodedEnvelope) {
        try {
            byte[] envelope = Base64.getDecoder().decode(encodedEnvelope);
            ByteBuffer buffer = ByteBuffer.wrap(envelope);
            if (buffer.remaining() < 2 || buffer.get() != FORMAT_VERSION) {
                throw new IllegalStateException("Unsupported idempotency response envelope version");
            }
            int keyIdLength = Byte.toUnsignedInt(buffer.get());
            if (keyIdLength == 0 || buffer.remaining() < keyIdLength + NONCE_BYTES + 16) {
                throw new IllegalStateException("Malformed idempotency response envelope");
            }
            byte[] keyIdBytes = new byte[keyIdLength];
            buffer.get(keyIdBytes);
            String keyId = new String(keyIdBytes, StandardCharsets.UTF_8);
            byte[] key = keys.get(keyId);
            if (key == null) {
                throw new IllegalStateException("Required historical idempotency encryption key is unavailable");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(idempotencyKey.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not authenticate/decrypt idempotency response", exception);
        }
    }
}
