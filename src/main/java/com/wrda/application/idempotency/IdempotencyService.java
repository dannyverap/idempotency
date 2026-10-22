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
 * La reserva se confirma antes del negocio. La finalización se une a la transacción de negocio
 * para que sus escrituras y el replay se confirmen o reviertan juntos.
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

    // 1) Intento atomico de reclamar la key (primera sentencia = escritura).
    if (store.insertInProgress(key, requestHash, now, expiresAt)) {
      return new AcquireResult.Acquired();
    }

    // 2) Ya existia: ver en que estado esta.
    IdempotencyEntry existing = store.findEntry(key);
    if (existing == null) {
      return null; // la liberaron justo ahora: reintenta el INSERT
    }

    // No retomar automáticamente por antigüedad: la operación original podría seguir viva.
    if (!existing.requestHash().equals(requestHash)) {
      return new AcquireResult.KeyReused();
    }

    // Terminada con éxito: replay. IN_PROGRESS antiguo requiere recuperación operativa.
    if (existing.status() == IdempotencyStatus.COMPLETED) {
      return new AcquireResult.Replay(new AcquireResult.StoredResponse(
          existing.statusCode(), existing.responseSnapshot()));
    }
    return new AcquireResult.InProgress();
  }

  @Transactional
  public void complete(String key, int statusCode, String responseSnapshot) {
    long now = System.currentTimeMillis();
    if (!store.markCompleted(key, statusCode, responseSnapshot, now, now + ttl.toMillis())) {
      throw new IllegalStateException("Idempotency reservation could not be completed");
    }
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
