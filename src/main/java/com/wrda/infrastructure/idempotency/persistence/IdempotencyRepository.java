package com.wrda.infrastructure.idempotency.persistence;

import com.wrda.application.idempotency.model.IdempotencyEntry;
import com.wrda.application.idempotency.model.IdempotencyStatus;
import com.wrda.application.idempotency.port.out.IdempotencyStore;
import com.wrda.infrastructure.error.InfrastructureException;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Supplier;
import org.hibernate.Session;

/**
 * Cada metodo es UNA sentencia SQL atomica. Se evita a proposito el patron "consultar y luego
 * insertar" y las excepciones por PK duplicada (dejarian la transaccion en rollback-only). H2
 * ignora el conflicto de PK con MERGE WHEN NOT MATCHED, manteniendo la reclamacion atomica sin
 * cambiar una key existente.
 */
@ApplicationScoped
public class IdempotencyRepository
    implements PanacheRepositoryBase<IdempotencyRecord, String>, IdempotencyStore {

  /*
   * Reclama la key en una sola operacion atomica:
   * - USING construye la fila candidata con los parametros recibidos y estado IN_PROGRESS.
   * - ON busca si ya existe una fila con la misma idempotency_key.
   * - WHEN NOT MATCHED inserta la candidata solo cuando no encontro esa key.
   * Si ya existe, no hay clausula WHEN MATCHED, por lo que la fila queda intacta.
   *
   * Se usa MERGE en vez de un INSERT simple porque dos solicitudes concurrentes pueden
   * intentar insertar la misma key: el INSERT podria fallar por clave primaria duplicada
   * y marcar la transaccion como fallida. MERGE resuelve el intento de insercion de forma
   * atomica; executeUpdate devuelve 1 si esta solicitud inserto la fila y 0 si ya existia.
   */
  @SuppressWarnings("SqlResolve")
  private static final String INSERT_IN_PROGRESS_SQL =
      "MERGE INTO idempotency_keys "
          + "USING (VALUES (?1, ?2, 'IN_PROGRESS', ?3, ?4, ?5)) "
          + "AS new_key(idempotency_key, request_hash, status, created_at, updated_at, expires_at) "
          + "ON idempotency_keys.idempotency_key = new_key.idempotency_key "
          + "WHEN NOT MATCHED THEN "
          + "INSERT (idempotency_key, request_hash, status, created_at, updated_at, expires_at) "
          + "VALUES (new_key.idempotency_key, new_key.request_hash, new_key.status, "
          + "new_key.created_at, new_key.updated_at, new_key.expires_at)";

  @Override
  public IdempotencyEntry findEntry(String key) {
    return persistence("read idempotency key", () -> {
      IdempotencyRecord idempotencyRecord = find("idempotencyKey", key).firstResult();
      return idempotencyRecord == null ? null
          : new IdempotencyEntry(idempotencyRecord.requestHash, idempotencyRecord.status,
              idempotencyRecord.statusCode, idempotencyRecord.encryptedResponse,
              idempotencyRecord.createdAt, idempotencyRecord.updatedAt,
              idempotencyRecord.expiresAt);
    });
  }

  /**
   * Intenta reclamar una key insertando su registro en estado {@code IN_PROGRESS}.
   * La sentencia {@code MERGE} comprueba e inserta de forma atomica: si otra solicitud
   * ya registro la misma key, no modifica ese registro ni crea uno duplicado.
   *
   * @param key clave de idempotencia que se intenta reclamar
   * @param requestHash hash de la solicitud, guardado para detectar reutilizacion con otro contenido
   * @param now instante actual, usado como fecha de creacion y ultima actualizacion
   * @param expiresAt instante en que vence el registro
   * @return {@code true} si esta llamada inserto el registro y puede procesar la solicitud;
   *         {@code false} si la key ya estaba registrada y debe evaluarse su estado existente
   */
  @Override
  // La Session pertenece al EntityManager; cerrarla aqui interrumpiria la transaccion.
  @SuppressWarnings("resource")
  public boolean insertInProgress(String key, String requestHash, long now, long expiresAt) {
    return persistence("claim idempotency key", () -> {
      int rows = getEntityManager().unwrap(Session.class)
          .createNativeMutationQuery(INSERT_IN_PROGRESS_SQL)
          .setParameter(1, key)
          .setParameter(2, requestHash)
          .setParameter(3, now)
          .setParameter(4, now)
          .setParameter(5, expiresAt)
          .executeUpdate();
      return rows == 1;
    });
  }

  /** IN_PROGRESS -> COMPLETED en la misma transacción que las escrituras del negocio. */
  @Override
  public boolean markCompleted(String key, int statusCode, String encryptedResponse,
      long now, long expiresAt) {
    return persistence("complete idempotency key", () -> {
      int rows = update("status = ?1, statusCode = ?2, encryptedResponse = ?3, "
              + "updatedAt = ?4, expiresAt = ?5 where idempotencyKey = ?6 and status = ?7",
          IdempotencyStatus.COMPLETED, statusCode, encryptedResponse, now, expiresAt,
          key, IdempotencyStatus.IN_PROGRESS);
      return rows == 1;
    });
  }

  /**
   * Borra el IN_PROGRESS (si falla la ejecucion) para no bloquear reintentos legitimos.
   */
  @Override
  public long releaseInProgress(String key) {
    return persistence("release idempotency key", () ->
        delete("idempotencyKey = ?1 and status = ?2", key, IdempotencyStatus.IN_PROGRESS));
  }

  @Override
  public long deleteExpired(long now) {
    return persistence("delete expired idempotency keys", () ->
        delete("status = ?1 and expiresAt < ?2", IdempotencyStatus.COMPLETED, now));
  }

  private <T> T persistence(String operation, Supplier<T> action) {
    try {
      return action.get();
    } catch (RuntimeException exception) {
      if (exception instanceof InfrastructureException infrastructureException) {
        throw infrastructureException;
      }
      throw new InfrastructureException("IDEMPOTENCY_STORAGE_FAILURE",
          "Could not " + operation, exception);
    }
  }
}
