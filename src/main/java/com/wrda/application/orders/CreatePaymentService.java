package com.wrda.application.orders;

import com.wrda.application.orders.port.out.OrderStore;
import com.wrda.application.orders.port.out.PaymentStore;
import com.wrda.domain.Order;
import com.wrda.domain.Payment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@ApplicationScoped
public class CreatePaymentService {

    private final OrderStore orderStore;
    private final PaymentStore paymentStore;

    @Inject
    public CreatePaymentService(OrderStore orderStore, PaymentStore paymentStore) {
        this.orderStore = orderStore;
        this.paymentStore = paymentStore;
    }

    @Transactional
    public Payment create(CreatePaymentCommand command) {
        Order order = orderStore.findOrder(command.orderId());
        if (order == null) {
            throw new OrderNotFoundException(command.orderId());
        }
        Payment payment = Payment.create(UUID.randomUUID().toString(), order.orderId(),
                command.amount(), Instant.now().truncatedTo(ChronoUnit.MILLIS));
        if (command.simulateFailure()) {
            throw new OrderProcessingException();
        }
        paymentStore.save(payment);
        return payment;
    }

    @Transactional
    public Payment createForOrder(String orderId, java.math.BigDecimal amount,
                                  boolean simulateFailure) {
        Payment payment = Payment.create(UUID.randomUUID().toString(), orderId, amount,
                Instant.now().truncatedTo(ChronoUnit.MILLIS));
        if (simulateFailure) {
            throw new OrderProcessingException();
        }
        paymentStore.save(payment);
        return payment;
    }
}
