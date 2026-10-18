package com.wrda.infrastructure.idempotency.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrda.application.idempotency.IdempotencyService;
import com.wrda.application.idempotency.model.AcquireResult;
import com.wrda.infrastructure.error.InfrastructureException;
import com.wrda.infrastructure.orders.contract.model.AtlasError;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.HeaderParam;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
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
    if (!(ctx.getTarget() instanceof IdempotencyReplayHandler)) {
      throw new InfrastructureException("IDEMPOTENCY_REPLAY_HANDLER_REQUIRED",
          "An @WrdaIdempotency endpoint must implement IdempotencyReplayHandler", null);
    }

    String key = routingContext.request().getHeader(HEADER);
    if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
      return problem(400, "IDEMPOTENCY_KEY_REQUIRED",
          "El header Idempotency-Key es obligatorio (maximo " + MAX_KEY_LENGTH + " caracteres)")
          .build();
    }

    String requestHash = fingerprint(ctx);
    String storedKey = sha256(key);
    AcquireResult result = service.acquire(storedKey, requestHash);

    if (result instanceof AcquireResult.Acquired) {
      return execute(ctx, storedKey, (IdempotencyReplayHandler) ctx.getTarget());
    }
    if (result instanceof AcquireResult.Replay(var response)) {
      return replay((IdempotencyReplayHandler) ctx.getTarget(), response);
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

  private Object execute(InvocationContext ctx, String key, IdempotencyReplayHandler replayHandler)
      throws Exception {
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
      completeQuietly(key, response, replayHandler);
    } else {
      releaseQuietly(key); // solo se guardan exitos
    }
    return result;
  }

  private void completeQuietly(String key, Response response, IdempotencyReplayHandler replayHandler) {
    try {
      String resourceReference = replayHandler.resourceReference(response);
      if (resourceReference == null || resourceReference.isBlank()) {
        throw new IllegalStateException(
            "Successful @WrdaIdempotency responses must provide a resource reference");
      }
      service.complete(key, response.getStatus(), resourceReference);
    } catch (Exception e) {
      // El negocio ya hizo commit: no se le debe fallar al cliente por esto.
      LOG.errorf(e,
          "No se pudo guardar la referencia de replay para la key hash %s; quedara IN_PROGRESS hasta el timeout",
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

  private Response replay(IdempotencyReplayHandler handler, AcquireResult.StoredResponse stored) {
    if (stored.resourceReference() == null || stored.resourceReference().isBlank()) {
      throw new InfrastructureException("IDEMPOTENCY_RESOURCE_REFERENCE_MISSING",
          "The completed idempotency record has no resource reference", null);
    }
    try (Response resourceResponse = handler.replay(stored.resourceReference(), stored.statusCode())) {
      return Response.fromResponse(resourceResponse)
          .header("Idempotency-Replayed", "true")
          .build();
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new InfrastructureException("IDEMPOTENCY_REPLAY_FAILURE",
          "Could not rebuild the response from its persisted resource", exception);
    }
  }

  /**
   * SHA-256 de metodo HTTP + path + parametros del metodo (el body ya deserializado).
   */
  private String fingerprint(InvocationContext ctx)
      throws NoSuchAlgorithmException, com.fasterxml.jackson.core.JsonProcessingException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update((routingContext.request().method().name() + " "
        + routingContext.request().path() + "\n").getBytes(StandardCharsets.UTF_8));
    Object[] parameters = ctx.getParameters();
    var methodParameters = ctx.getMethod().getParameters();
    List<Object> requestValues = new ArrayList<>(parameters.length);
    for (int i = 0; i < parameters.length; i++) {
      HeaderParam headerParam = methodParameters[i].getAnnotation(HeaderParam.class);
      if (headerParam == null || !HEADER.equals(headerParam.value())) {
        requestValues.add(parameters[i]);
      }
    }
    digest.update(mapper.writeValueAsBytes(requestValues));
    return HexFormat.of().formatHex(digest.digest());
  }

  private static String sha256(String value) throws NoSuchAlgorithmException {
    return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }
}
