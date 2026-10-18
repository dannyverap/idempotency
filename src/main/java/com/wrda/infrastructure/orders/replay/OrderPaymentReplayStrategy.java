package com.wrda.infrastructure.orders.replay;

import com.wrda.application.orders.GetOrderService;
import com.wrda.application.orders.GetPaymentService;
import com.wrda.domain.Order;
import com.wrda.domain.OrderPayment;
import com.wrda.domain.Payment;
import com.wrda.infrastructure.error.InfrastructureException;
import com.wrda.infrastructure.idempotency.http.IdempotencyReplayStrategy;
import com.wrda.infrastructure.orders.http.OrderApiMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.UUID;

@ApplicationScoped
public class OrderPaymentReplayStrategy implements IdempotencyReplayStrategy {

    private final GetOrderService getOrderService;
    private final GetPaymentService getPaymentService;

    @Inject
    public OrderPaymentReplayStrategy(GetOrderService getOrderService,
                                      GetPaymentService getPaymentService) {
        this.getOrderService = getOrderService;
        this.getPaymentService = getPaymentService;
    }

    @Override
    public String resourceType() {
        return "order-payment";
    }

    @Override
    public Response replay(String resourceReference, int statusCode) {
        String[] identifiers = resourceReference.split(":", -1);
        if (identifiers.length != 2) {
            throw new InfrastructureException("INVALID_REPLAY_REFERENCE",
                    "Order/payment replay reference must contain both resource identifiers", null);
        }
        UUID orderId = ReplayReferenceParser.parseUuid(identifiers[0], "order");
        UUID paymentId = ReplayReferenceParser.parseUuid(identifiers[1], "payment");
        Order order = getOrderService.findById(orderId.toString())
                .orElseThrow(() -> new InfrastructureException("ORDER_NOT_FOUND_FOR_REPLAY",
                        "The order referenced by the idempotency record was not found", null));
        Payment payment = getPaymentService.findById(paymentId.toString())
                .orElseThrow(() -> new InfrastructureException("PAYMENT_NOT_FOUND_FOR_REPLAY",
                        "The payment referenced by the idempotency record was not found", null));
        if (!payment.orderId().equals(order.orderId())) {
            throw new InfrastructureException("REPLAY_RESOURCE_MISMATCH",
                    "The payment does not belong to the referenced order", null);
        }
        return Response.status(statusCode)
                .location(URI.create("/api/orders/" + orderId))
                .entity(OrderApiMapper.toOrderPaymentResponse(new OrderPayment(order, payment)))
                .build();
    }
}
