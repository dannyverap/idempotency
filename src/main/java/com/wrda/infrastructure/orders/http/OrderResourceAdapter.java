package com.wrda.infrastructure.orders.http;

import com.wrda.application.orders.CreateOrderCommand;
import com.wrda.application.orders.CreateOrderPaymentCommand;
import com.wrda.application.orders.CreateOrderPaymentService;
import com.wrda.application.orders.CreateOrderService;
import com.wrda.application.orders.GetOrderService;
import com.wrda.domain.Order;
import com.wrda.infrastructure.error.InfrastructureException;
import com.wrda.infrastructure.error.WrdaErrorManagement;
import com.wrda.infrastructure.error.WrdaManagedEndpoint;
import com.wrda.infrastructure.orders.contract.api.OrdersApi;
import com.wrda.infrastructure.orders.contract.model.CreateOrderRequest;
import com.wrda.infrastructure.orders.contract.model.CreateOrderPaymentRequest;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.UUID;

public class OrderResourceAdapter implements OrdersApi {

    private final CreateOrderService createOrderService;
    private final CreateOrderPaymentService createOrderPaymentService;
    private final GetOrderService getOrderService;

    @Inject
    public OrderResourceAdapter(CreateOrderService createOrderService,
                                CreateOrderPaymentService createOrderPaymentService,
                                GetOrderService getOrderService) {
        this.createOrderService = createOrderService;
        this.createOrderPaymentService = createOrderPaymentService;
        this.getOrderService = getOrderService;
    }

    @Override
    @Blocking
    @WrdaManagedEndpoint
    @Transactional
    public Response createOrder(@HeaderParam("Idempotency-Key") String idempotencyKey,
                                CreateOrderRequest request) {
        Log.info("Processing idempotent order request");
        CreateOrderCommand command = new CreateOrderCommand(
                request == null ? null : request.getCustomerId(),
                request == null ? null : request.getAmount(),
                request != null && Boolean.TRUE.equals(request.getSimulateFailure()));
        Order order = createOrderService.create(command);

        return Response.created(URI.create("/api/orders/" + order.orderId()))
                .entity(OrderApiMapper.toOrderResponse(order))
                .build();
    }

    @Override
    @Blocking
    @WrdaManagedEndpoint
    @Transactional
    public Response createOrderWithPayment(String idempotencyKey,
                                           CreateOrderPaymentRequest request) {
        Log.info("Processing idempotent order-and-payment request");
        var command = new CreateOrderPaymentCommand(
                request == null ? null : request.getCustomerId(),
                request == null ? null : request.getAmount(),
                request != null && Boolean.TRUE.equals(request.getSimulatePaymentFailure()));
        var orderPayment = createOrderPaymentService.create(command);
        return Response.created(URI.create("/api/orders/" + orderPayment.order().orderId()))
                .entity(OrderApiMapper.toOrderPaymentResponse(orderPayment))
                .build();
    }

    @Override
    @Blocking
    @WrdaErrorManagement
    public Response getOrder(UUID orderId) {
        Order order = getOrderService.findById(orderId.toString())
                .orElseThrow(() -> new InfrastructureException("ORDER_NOT_FOUND",
                        "Order referenced by the idempotency record was not found", null));
        return Response.ok(OrderApiMapper.toOrderResponse(order)).build();
    }

}
