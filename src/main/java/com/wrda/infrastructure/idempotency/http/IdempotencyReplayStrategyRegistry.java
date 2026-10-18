package com.wrda.infrastructure.idempotency.http;

import com.wrda.infrastructure.error.InfrastructureException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class IdempotencyReplayStrategyRegistry {

    private final Instance<IdempotencyReplayStrategy> strategies;

    @Inject
    public IdempotencyReplayStrategyRegistry(Instance<IdempotencyReplayStrategy> strategies) {
        this.strategies = strategies;
    }

    public Response replay(String resourceType, String resourceReference, int statusCode) {
        IdempotencyReplayStrategy match = null;
        for (IdempotencyReplayStrategy strategy : strategies) {
            if (strategy.resourceType().equals(resourceType)) {
                if (match != null) {
                    throw new InfrastructureException("DUPLICATE_REPLAY_STRATEGY",
                            "More than one replay strategy is registered for " + resourceType, null);
                }
                match = strategy;
            }
        }
        if (match == null) {
            throw new InfrastructureException("REPLAY_STRATEGY_NOT_FOUND",
                    "No replay strategy is registered for " + resourceType, null);
        }
        return match.replay(resourceReference, statusCode);
    }
}
