package com.wrda.application.orders;

import com.wrda.application.orders.port.out.PaymentStore;
import com.wrda.domain.Payment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class GetPaymentService {

    private final PaymentStore paymentStore;

    @Inject
    public GetPaymentService(PaymentStore paymentStore) {
        this.paymentStore = paymentStore;
    }

    public Optional<Payment> findById(String paymentId) {
        return Optional.ofNullable(paymentStore.findPayment(paymentId));
    }
}
