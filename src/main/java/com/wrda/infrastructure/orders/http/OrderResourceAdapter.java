package com.wrda.infrastructure.orders.http;

import com.wrda.application.orders.CreateOrderCommand;
import com.wrda.application.orders.CreateOrderService;
import com.wrda.application.orders.GetOrderService;
import com.wrda.domain.Order;
import com.wrda.infrastructure.error.InfrastructureException;
import com.wrda.infrastructure.error.WrdaErrorManagement;
import com.wrda.infrastructure.error.WrdaManagedEndpoint;
import com.wrda.infrastructure.orders.contract.api.OrdersApi;
import com.wrda.infrastructure.orders.contract.model.CreateOrderRequest;
import com.wrda.infrastructure.orders.contract.model.OrderResponse;
import com.wrda.infrastructure.idempotency.http.IdempotencyReplayHandler;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.Date;
import java.util.UUID;

public class OrderResourceAdapter implements OrdersApi, IdempotencyReplayHandler {

    private final CreateOrderService createOrderService;
    private final GetOrderService getOrderService;

    @Inject
    public OrderResourceAdapter(CreateOrderService createOrderService, GetOrderService getOrderService) {
        this.createOrderService = createOrderService;
        this.getOrderService = getOrderService;
    }

    @Override
    @Blocking
    @WrdaManagedEndpoint
    public Response createOrder(@HeaderParam("Idempotency-Key") String idempotencyKey,
                                CreateOrderRequest request) {
        Log.infof("Procesando orden (esto es lo que NO debe repetirse): %s", request);
        CreateOrderCommand command = new CreateOrderCommand(
                request == null ? null : request.getCustomerId(),
                request == null ? null : request.getAmount(),
                request != null && Boolean.TRUE.equals(request.getSimulateFailure()));
        Order order = createOrderService.create(command);

        return Response.created(URI.create("/api/orders/" + order.orderId()))
                .entity(toResponse(order))
                .build();
    }

    @Override
    @Blocking
    @WrdaErrorManagement
    public Response getOrder(UUID orderId) {
        Order order = getOrderService.findById(orderId.toString())
                .orElseThrow(() -> new InfrastructureException("ORDER_NOT_FOUND",
                        "Order referenced by the idempotency record was not found", null));
        return Response.ok(toResponse(order)).build();
    }

    @Override
    public String resourceReference(Response successfulResponse) {
        URI location = successfulResponse.getLocation();
        if (location == null || location.getPath() == null) {
            return null;
        }
        String path = location.getPath();
        String orderId = path.substring(path.lastIndexOf('/') + 1);
        return UUID.fromString(orderId).toString();
    }

    @Override
    public Response replay(String resourceReference, int statusCode) {
        UUID orderId = UUID.fromString(resourceReference);
        Order order = getOrderService.findById(orderId.toString())
                .orElseThrow(() -> new InfrastructureException("ORDER_NOT_FOUND_FOR_REPLAY",
                        "The order referenced by the idempotency record was not found", null));
        return Response.status(statusCode)
                .location(URI.create("/api/orders/" + orderId))
                .entity(toResponse(order))
                .build();
    }

    private static OrderResponse toResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(UUID.fromString(order.orderId()));
        response.setCustomerId(order.customerId());
        response.setAmount(order.amount().stripTrailingZeros());
        response.setStatus(OrderResponse.StatusEnum.CREATED);
        response.setCreatedAt(Date.from(order.createdAt()));
        return response;
    }
}
