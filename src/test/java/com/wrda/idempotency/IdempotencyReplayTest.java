package com.wrda.idempotency;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class IdempotencyReplayTest {
    @Test
    void sameRequestReplaysIdenticalResponseAndDifferentPayloadIsRejected() {
        String key = UUID.randomUUID().toString();
        String body = "{\"customerId\":\"TEST-1\",\"amount\":25.25}";
        Response first = post("/api/orders", key, body);
        Response replay = post("/api/orders", key, body);

        assertEquals(201, first.statusCode());
        assertEquals(201, replay.statusCode());
        assertEquals("true", replay.getHeader("Idempotency-Replayed"));
        assertArrayEquals(first.asByteArray(), replay.asByteArray());

        Response changed = post("/api/orders", key, "{\"customerId\":\"TEST-1\",\"amount\":26.25}");
        assertEquals(422, changed.statusCode());
    }

    @Test
    void concurrentDuplicateIsRejectedAndCompositeReplayIsExact() throws Exception {
        String concurrentKey = UUID.randomUUID().toString();
        String body = "{\"customerId\":\"TEST-2\",\"amount\":39.00}";
        CompletableFuture<Response> firstCall = CompletableFuture.supplyAsync(
                () -> post("/api/orders", concurrentKey, body));
        Thread.sleep(250);
        Response concurrent = post("/api/orders", concurrentKey, body);
        assertEquals(409, concurrent.statusCode());
        assertEquals(201, firstCall.get(15, TimeUnit.SECONDS).statusCode());

        String compositeKey = UUID.randomUUID().toString();
        String compositeBody = "{\"customerId\":\"TEST-3\",\"amount\":72.25}";
        Response composite = post("/api/orders/with-payment", compositeKey, compositeBody);
        Response compositeReplay = post("/api/orders/with-payment", compositeKey, compositeBody);
        assertEquals(201, composite.statusCode());
        assertEquals(201, compositeReplay.statusCode());
        assertEquals("true", compositeReplay.getHeader("Idempotency-Replayed"));
        assertArrayEquals(composite.asByteArray(), compositeReplay.asByteArray());
        assertTrue(new String(composite.asByteArray()).contains("payment"));
    }

    @Test
    void failedOperationReleasesReservationForRetry() {
        String key = UUID.randomUUID().toString();
        String body = "{\"customerId\":\"TEST-4\",\"amount\":11.00,\"simulateFailure\":true}";
        Response firstFailure = post("/api/orders", key, body);
        Response retry = post("/api/orders", key, body);
        assertEquals(500, firstFailure.statusCode());
        // A stuck reservation would return 409; 500 proves the request was retried through the handler.
        assertEquals(500, retry.statusCode());
    }

    private Response post(String path, String key, String body) {
        return given()
                .contentType("application/json")
                .header("Idempotency-Key", key)
                .body(body)
                .when()
                .post(path)
                .then()
                .extract()
                .response();
    }
}
