package com.wrda.application.idempotency;

import com.wrda.application.idempotency.model.AcquireResult;
import com.wrda.application.idempotency.model.IdempotencyEntry;
import com.wrda.application.idempotency.model.IdempotencyStatus;
import com.wrda.application.idempotency.port.out.IdempotencyStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Todas las operaciones corren en su PROPIA transaccion corta (REQUIRES_NEW). Asi el IN_PROGRESS
 * queda confirmado (visible para otras peticiones) antes de ejecutar el negocio, y complete/release
 * no dependen de la transaccion del negocio.
 */
@ApplicationScoped
public class IdempotencyService {

  private final IdempotencyStore store;

  @Inject
  public IdempotencyService(IdempotencyStore store) {
    this.store = store;
  }

  @ConfigProperty(name = "wrda.idempotency.ttl", defaultValue = "PT24H")
  Duration ttl;

  @ConfigProperty(name = "wrda.idempotency.in-progress-timeout", defaultValue = "PT30S")
  Duration inProgressTimeout;

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public AcquireResult acquire(String key, String requestHash) {
    for (int attempt = 0; attempt < 3; attempt++) {
      AcquireResult result = tryAcquire(key, requestHash);
      if (result != null) {
        return result;
      }
    }
    return new AcquireResult.InProgress();
  }

  private AcquireResult tryAcquire(String key, String requestHash) {
    long now = System.currentTimeMillis();
    long expiresAt = now + ttl.toMillis();
    long staleBefore = now - inProgressTimeout.toMillis();

    // 1) Intento atomico de reclamar la key (primera sentencia = escritura).
    if (store.insertInProgress(key, requestHash, now, expiresAt)) {
      return new AcquireResult.Acquired();
    }

    // 2) Ya existia: ver en que estado esta.
    IdempotencyEntry existing = store.findEntry(key);
    if (existing == null) {
      return null; // la liberaron justo ahora: reintenta el INSERT
    }

    // 3) IN_PROGRESS huerfano (el proceso murio): se puede retomar.
    if (!existing.requestHash().equals(requestHash)) {
      return new AcquireResult.KeyReused();
    }

    if (existing.status() == IdempotencyStatus.IN_PROGRESS
        && existing.updatedAt() < staleBefore) {
      return store.takeOverStale(key, requestHash, now, expiresAt, staleBefore)
          ? new AcquireResult.Acquired()
          : null; // otro la retomo/cambio: reevaluar
    }

    // Terminada con exito: replay. Si no, sigue en ejecucion.
    if (existing.status() == IdempotencyStatus.COMPLETED) {
      return new AcquireResult.Replay(new AcquireResult.StoredResponse(
          existing.statusCode(), existing.responseBody(),
          existing.contentType(), existing.location()));
    }
    return new AcquireResult.InProgress();
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void complete(String key, int statusCode, String body, String contentType,
      String location) {
    store.markCompleted(key, statusCode, body, contentType, location, System.currentTimeMillis());
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void release(String key) {
    store.releaseInProgress(key);
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public long purgeExpired() {
    return store.deleteExpired(System.currentTimeMillis());
  }
}
