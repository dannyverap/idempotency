package com.wrda.infrastructure.orders.replay;

import com.wrda.application.orders.GetPaymentService;
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
public class PaymentReplayStrategy implements IdempotencyReplayStrategy {

    private final GetPaymentService getPaymentService;

    @Inject
    public PaymentReplayStrategy(GetPaymentService getPaymentService) {
        this.getPaymentService = getPaymentService;
    }

    @Override
    public String resourceType() {
        return "payment";
    }

    @Override
    public Response replay(String resourceReference, int statusCode) {
        UUID paymentId = ReplayReferenceParser.parseUuid(resourceReference, "payment");
        Payment payment = getPaymentService.findById(paymentId.toString())
                .orElseThrow(() -> new InfrastructureException("PAYMENT_NOT_FOUND_FOR_REPLAY",
                        "The payment referenced by the idempotency record was not found", null));
        return Response.status(statusCode)
                .location(URI.create("/api/payments/" + paymentId))
                .entity(OrderApiMapper.toPaymentResponse(payment))
                .build();
    }
}
