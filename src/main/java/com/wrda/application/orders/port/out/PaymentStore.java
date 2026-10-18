package com.wrda.application.orders.port.out;

import com.wrda.domain.Payment;

public interface PaymentStore {

    void save(Payment payment);

    Payment findPayment(String paymentId);
}
