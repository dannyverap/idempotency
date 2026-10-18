package com.wrda.infrastructure.orders.replay;

import com.wrda.infrastructure.error.InfrastructureException;

import java.util.UUID;

final class ReplayReferenceParser {

    private ReplayReferenceParser() {
    }

    static UUID parseUuid(String value, String resourceType) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new InfrastructureException("INVALID_REPLAY_REFERENCE",
                    "The stored " + resourceType + " replay reference is not a valid UUID", exception);
        }
    }
}
