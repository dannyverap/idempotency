package com.wrda.infrastructure.idempotency.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrda.application.idempotency.IdempotencyService;
import com.wrda.application.idempotency.model.AcquireResult;
import com.wrda.infrastructure.orders.contract.model.AtlasError;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.jboss.logging.Logger;

/**
 * Prioridad 100 < 200 (la del @Transactional de Quarkus): este interceptor corre POR FUERA de
 * cualquier @Transactional del metodo de negocio. Asi complete/release ocurren despues del
 * commit/rollback del negocio.
 */
@WrdaIdempotency
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 100)
public class WrdaIdempotencyInterceptor {

  static final String HEADER = "Idempotency-Key";
  static final int MAX_KEY_LENGTH = 100;
  private static final Logger LOG = Logger.getLogger(WrdaIdempotencyInterceptor.class);

  private final RoutingContext routingContext;
  private final IdempotencyService service;

  private final ObjectMapper mapper;

  @Inject
  public WrdaIdempotencyInterceptor(RoutingContext routingContext, IdempotencyService service,
      ObjectMapper mapper) {
    this.routingContext = routingContext;
    this.service = service;
    this.mapper = mapper;
  }

  private static Response.ResponseBuilder problem(int status, String code, String message) {
    AtlasError error = new AtlasError();
    error.setCode(code);
    error.setMessage(message);
    error.setCategory(AtlasError.CategoryEnum.IDEMPOTENCY);
    return Response.status(status)
        .type(MediaType.APPLICATION_JSON)
        .entity(error);
  }

  // --- Ejecucion del negocio ------------------------------------------------

  @AroundInvoke
  @SuppressWarnings("unused")
  Object around(InvocationContext ctx) throws Exception {
    if (!Response.class.equals(ctx.getMethod().getReturnType())) {
      throw new IllegalStateException("@WrdaIdempotency requiere que " + ctx.getMethod()
          + " retorne jakarta.ws.rs.core.Response");
    }

    String key = routingContext.request().getHeader(HEADER);
    if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
      return problem(400, "IDEMPOTENCY_KEY_REQUIRED",
          "El header Idempotency-Key es obligatorio (maximo " + MAX_KEY_LENGTH + " caracteres)")
          .build();
    }

    String requestHash = fingerprint(ctx);
    AcquireResult result = service.acquire(key, requestHash);

    if (result instanceof AcquireResult.Acquired) {
      return execute(ctx, key);
    }
    if (result instanceof AcquireResult.Replay(var response)) {
      return replay(response);
    }
    if (result instanceof AcquireResult.InProgress) {
      return problem(425, "REQUEST_IN_PROGRESS",
          "Una peticion con esta Idempotency-Key aun se esta procesando. Reintenta en unos segundos.")
          .header("Retry-After", "1")
          .build();
    }
    if (result instanceof AcquireResult.KeyReused) {
      return problem(422, "IDEMPOTENCY_KEY_REUSED",
          "Esta Idempotency-Key ya se uso con un request distinto.")
          .build();
    }
    throw new IllegalStateException("Resultado de adquisicion de idempotencia desconocido");
  }

  private Object execute(InvocationContext ctx, String key) throws Exception {
    Object result;
    try {
      result = ctx.proceed();
    } catch (Exception e) {
      releaseQuietly(key); // fallo: no dejar la key bloqueando reintentos
      throw e;
    }

    if (result == null) {
      releaseQuietly(key);
      return null;
    }

    Response response = (Response) result;
    int status = response.getStatus();
    if (status == 200 || status == 201) {
      completeQuietly(key, response);
    } else {
      releaseQuietly(key); // solo se guardan exitos
    }
    return result;
  }

  private void completeQuietly(String key, Response response) {
    try {
      String body = response.hasEntity() ? mapper.writeValueAsString(response.getEntity()) : null;
      String contentType = response.getMediaType() != null
          ? response.getMediaType().toString() : MediaType.APPLICATION_JSON;
      String location = response.getLocation() != null ? response.getLocation().toString() : null;
      service.complete(key, response.getStatus(), body, contentType, location);
    } catch (Exception e) {
      // El negocio ya hizo commit: no se le debe fallar al cliente por esto.
      LOG.errorf(e,
          "No se pudo guardar la respuesta de la key %s; quedara IN_PROGRESS hasta el timeout",
          key);
    }
  }

  // --- Replay ---------------------------------------------------------------

  private void releaseQuietly(String key) {
    try {
      service.release(key);
    } catch (Exception e) {
      LOG.errorf(e, "No se pudo liberar la key %s; quedara IN_PROGRESS hasta el timeout", key);
    }
  }

  // --- Utilidades -----------------------------------------------------------

  private Response replay(AcquireResult.StoredResponse stored)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    Response.ResponseBuilder builder = Response.status(stored.statusCode())
        .type(stored.contentType() != null ? stored.contentType() : MediaType.APPLICATION_JSON)
        .header("Idempotency-Replayed", "true");
    if (stored.body() != null) {
      // Se re-parsea el JSON para que Jackson lo escriba tal cual (sin doble escape).
      builder.entity(mapper.readTree(stored.body()));
    }
    if (stored.location() != null) {
      builder.location(URI.create(stored.location()));
    }
    return builder.build();
  }

  /**
   * SHA-256 de metodo HTTP + path + parametros del metodo (el body ya deserializado).
   */
  private String fingerprint(InvocationContext ctx)
      throws NoSuchAlgorithmException, com.fasterxml.jackson.core.JsonProcessingException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update((routingContext.request().method().name() + " "
        + routingContext.request().path() + "\n").getBytes(StandardCharsets.UTF_8));
    digest.update(mapper.writeValueAsBytes(ctx.getParameters()));
    return HexFormat.of().formatHex(digest.digest());
  }
}
