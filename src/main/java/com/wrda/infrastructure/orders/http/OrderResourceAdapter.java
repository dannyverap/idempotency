package com.wrda.infrastructure.orders.http;

import com.wrda.application.orders.CreateOrderCommand;
import com.wrda.application.orders.CreateOrderService;
import com.wrda.domain.Order;
import com.wrda.infrastructure.error.WrdaManagedEndpoint;
import com.wrda.infrastructure.orders.contract.api.OrdersApi;
import com.wrda.infrastructure.orders.contract.model.CreateOrderRequest;
import com.wrda.infrastructure.orders.contract.model.OrderResponse;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public class OrderResourceAdapter implements OrdersApi {

    private final CreateOrderService createOrderService;

    @Inject
    public OrderResourceAdapter(CreateOrderService createOrderService) {
        this.createOrderService = createOrderService;
    }

    @Override
    @Blocking
    @WrdaManagedEndpoint
    public Response createOrder(String idempotencyKey, CreateOrderRequest request) {
        Log.infof("Procesando orden (esto es lo que NO debe repetirse): %s", request);
        CreateOrderCommand command = new CreateOrderCommand(
                request == null ? null : request.getCustomerId(),
                request == null ? null : request.getAmount(),
                request != null && Boolean.TRUE.equals(request.getSimulateFailure()));
        Order order = createOrderService.create(command);

        OrderResponse response = new OrderResponse();
        response.setOrderId(UUID.fromString(order.orderId()));
        response.setCustomerId(order.customerId());
        response.setAmount(order.amount());
        response.setStatus(OrderResponse.StatusEnum.CREATED);
        response.setCreatedAt(Date.from(Instant.from(order.createdAt())));

        return Response.created(URI.create("/api/orders/" + order.orderId()))
                .entity(response)
                .build();
    }
}
