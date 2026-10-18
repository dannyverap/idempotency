package com.wrda.application.idempotency.port.out;

import com.wrda.application.idempotency.model.IdempotencyEntry;

/** Persistence operations required by the idempotency use cases. */
public interface IdempotencyStore {

    boolean insertInProgress(String key, String requestHash, long now, long expiresAt);

    IdempotencyEntry findEntry(String key);

    boolean takeOverStale(String key, String requestHash, long now, long expiresAt, long staleBefore);

    boolean markCompleted(String key, int statusCode, String resourceReference, long now);

    long releaseInProgress(String key);

    long deleteExpired(long now);
}
