package com.wrda.idempotency;

import com.wrda.infrastructure.idempotency.http.IdempotencyKeyValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyKeyValidatorTest {
    private final IdempotencyKeyValidator validator = new IdempotencyKeyValidator();

    @Test
    void acceptsOneNonBlankKeyWithinLengthLimit() {
        var result = validator.validate(List.of("customer-operation-42"));

        assertTrue(result.isValid());
        assertEquals("customer-operation-42", result.key());
    }

    @Test
    void rejectsMissingOrRepeatedHeaderValues() {
        assertEquals("IDEMPOTENCY_KEY_REQUIRED", validator.validate(List.of()).errorCode());
        assertEquals("IDEMPOTENCY_KEY_REQUIRED", validator.validate(List.of("one", "two")).errorCode());
    }

    @Test
    void rejectsBlankOverlongAndControlCharacterKeys() {
        assertEquals("IDEMPOTENCY_KEY_INVALID", validator.validate(List.of("  ")).errorCode());
        assertEquals("IDEMPOTENCY_KEY_INVALID",
                validator.validate(List.of("k".repeat(IdempotencyKeyValidator.MAX_KEY_LENGTH + 1))).errorCode());
        assertFalse(validator.validate(List.of("bad\nkey")).isValid());
    }
}
