package com.wrda.infrastructure.idempotency.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.interceptor.InvocationContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Builds the stable request representation used to detect key reuse with different input. */
@ApplicationScoped
public class RequestFingerprintBuilder {
    private final ObjectMapper mapper;

    @Inject
    public RequestFingerprintBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String build(InvocationContext invocation, String principalScope,
            String method, String path, String query) throws Exception {
        Object[] arguments = invocation.getParameters();
        var parameterMetadata = invocation.getMethod().getParameters();
        List<Object> requestArguments = new ArrayList<>(arguments.length);
        for (int index = 0; index < arguments.length; index++) {
            HeaderParam header = parameterMetadata[index].getAnnotation(HeaderParam.class);
            if (header == null || !IdempotencyKeyValidator.HEADER_NAME.equalsIgnoreCase(header.value())) {
                requestArguments.add(arguments[index]);
            }
        }

        byte[] serializedArguments = mapper.writeValueAsBytes(requestArguments);
        return principalScope + "\n" + method + "\n" + path + "\n"
                + (query == null ? "" : query) + "\n"
                + new String(serializedArguments, StandardCharsets.UTF_8);
    }
}
