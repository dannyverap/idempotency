package com.wrda.infrastructure.idempotency.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable HTTP result captured before the transaction commits. */
public record ResponseSnapshot(int status, String mediaType, Map<String, List<String>> headers, String body) {
    private static final List<String> REPLAYABLE_HEADERS = List.of(
            "location", "cache-control", "etag", "last-modified", "expires", "content-language", "vary");

    public static ResponseSnapshot capture(Response response, ObjectMapper mapper) throws IOException {
        String mediaType = response.getMediaType() == null
                ? MediaType.APPLICATION_JSON : response.getMediaType().toString();
        String body = null;
        Object entity = response.getEntity();
        if (entity != null) {
            if (mediaType.toLowerCase(Locale.ROOT).startsWith("application/json")
                    || mediaType.toLowerCase(Locale.ROOT).endsWith("+json")) {
                body = mapper.writeValueAsString(entity);
            } else if (mediaType.toLowerCase(Locale.ROOT).startsWith("text/")) {
                if (!(entity instanceof String text)) {
                    throw new IllegalArgumentException("Text idempotent responses must have a String entity");
                }
                body = text;
            } else {
                throw new IllegalArgumentException("Idempotent replay currently supports JSON and text responses only");
            }
        }
        Map<String, List<String>> safeHeaders = new LinkedHashMap<>();
        response.getStringHeaders().forEach((name, values) -> {
            if (REPLAYABLE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                safeHeaders.put(name, List.copyOf(values));
            }
        });
        return new ResponseSnapshot(response.getStatus(), mediaType, Map.copyOf(safeHeaders), body);
    }

    public Response toResponse() {
        Response.ResponseBuilder builder = Response.status(status).type(mediaType);
        headers.forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        if (body != null) {
            builder.entity(body.getBytes(StandardCharsets.UTF_8));
        }
        return builder.build();
    }
}
