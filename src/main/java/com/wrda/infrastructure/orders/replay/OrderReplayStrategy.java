package com.wrda.infrastructure.orders.replay;

import com.wrda.application.orders.GetOrderService;
import com.wrda.domain.Order;
import com.wrda.infrastructure.error.InfrastructureException;
import com.wrda.infrastructure.idempotency.http.IdempotencyReplayStrategy;
import com.wrda.infrastructure.orders.http.OrderApiMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.UUID;

@ApplicationScoped
public class OrderReplayStrategy implements IdempotencyReplayStrategy {

    private final GetOrderService getOrderService;

    @Inject
    public OrderReplayStrategy(GetOrderService getOrderService) {
        this.getOrderService = getOrderService;
    }

    @Override
    public String resourceType() {
        return "order";
    }

    @Override
    public Response replay(String resourceReference, int statusCode) {
        UUID orderId = ReplayReferenceParser.parseUuid(resourceReference, "order");
        Order order = getOrderService.findById(orderId.toString())
                .orElseThrow(() -> new InfrastructureException("ORDER_NOT_FOUND_FOR_REPLAY",
                        "The order referenced by the idempotency record was not found", null));
        return Response.status(statusCode)
                .location(URI.create("/api/orders/" + orderId))
                .entity(OrderApiMapper.toOrderResponse(order))
                .build();
    }
}
