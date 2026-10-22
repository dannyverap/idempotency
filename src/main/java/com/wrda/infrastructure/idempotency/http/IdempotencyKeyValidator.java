package com.wrda.infrastructure.idempotency.http;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/** Validates the transport-level Idempotency-Key header before any persistence is attempted. */
@ApplicationScoped
public class IdempotencyKeyValidator {
    public static final String HEADER_NAME = "Idempotency-Key";
    public static final int MAX_KEY_LENGTH = 200;

    public Validation validate(List<String> values) {
        if (values == null || values.size() != 1) {
            return Validation.invalid("IDEMPOTENCY_KEY_REQUIRED",
                    "Debe enviarse exactamente un header Idempotency-Key");
        }

        String key = values.getFirst();
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH
                || key.chars().anyMatch(Character::isISOControl)) {
            return Validation.invalid("IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key está vacía, es demasiado larga o contiene caracteres de control");
        }
        return Validation.valid(key);
    }

    public record Validation(String key, String errorCode, String message) {
        public static Validation valid(String key) {
            return new Validation(key, null, null);
        }

        public static Validation invalid(String errorCode, String message) {
            return new Validation(null, errorCode, message);
        }

        public boolean isValid() {
            return key != null;
        }
    }
}
