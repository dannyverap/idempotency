package com.wrda.infrastructure.orders.http;

import com.wrda.application.orders.CreatePaymentCommand;
import com.wrda.application.orders.CreatePaymentService;
import com.wrda.application.orders.GetPaymentService;
import com.wrda.domain.Payment;
import com.wrda.infrastructure.error.InfrastructureException;
import com.wrda.infrastructure.error.WrdaErrorManagement;
import com.wrda.infrastructure.error.WrdaManagedEndpoint;
import com.wrda.infrastructure.idempotency.http.IdempotencyReplayHandler;
import com.wrda.infrastructure.idempotency.http.ReplayReference;
import com.wrda.infrastructure.orders.contract.api.PaymentsApi;
import com.wrda.infrastructure.orders.contract.model.CreatePaymentRequest;
import com.wrda.infrastructure.orders.contract.model.PaymentResponse;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.UUID;

public class PaymentResourceAdapter implements PaymentsApi, IdempotencyReplayHandler {

    private final CreatePaymentService createPaymentService;
    private final GetPaymentService getPaymentService;

    @Inject
    public PaymentResourceAdapter(CreatePaymentService createPaymentService,
                                  GetPaymentService getPaymentService) {
        this.createPaymentService = createPaymentService;
        this.getPaymentService = getPaymentService;
    }

    @Override
    @Blocking
    @WrdaManagedEndpoint
    public Response createPayment(@HeaderParam("Idempotency-Key") String idempotencyKey,
                                  CreatePaymentRequest request) {
        var command = new CreatePaymentCommand(
                request == null || request.getOrderId() == null
                        ? null : request.getOrderId().toString(),
                request == null ? null : request.getAmount(),
                request != null && Boolean.TRUE.equals(request.getSimulateFailure()));
        Payment payment = createPaymentService.create(command);
        return Response.created(URI.create("/api/payments/" + payment.paymentId()))
                .entity(OrderApiMapper.toPaymentResponse(payment))
                .build();
    }

    @Override
    @Blocking
    @WrdaErrorManagement
    public Response getPayment(UUID paymentId) {
        Payment payment = getPaymentService.findById(paymentId.toString())
                .orElseThrow(() -> new InfrastructureException("PAYMENT_NOT_FOUND",
                        "Payment was not found", null));
        return Response.ok(OrderApiMapper.toPaymentResponse(payment)).build();
    }

    @Override
    public ReplayReference resourceReference(Response successfulResponse) {
        if (successfulResponse.getEntity() instanceof PaymentResponse response) {
            return new ReplayReference("payment", response.getPaymentId().toString());
        }
        throw new InfrastructureException("UNSUPPORTED_REPLAY_RESPONSE",
                "No replay reference extractor exists for this response type", null);
    }
}
