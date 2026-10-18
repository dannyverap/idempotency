package com.wrda.infrastructure.orders.persistence;

import com.wrda.application.orders.port.out.PaymentStore;
import com.wrda.domain.Payment;
import com.wrda.infrastructure.error.InfrastructureException;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;

@ApplicationScoped
public class PaymentRepository implements PanacheRepositoryBase<PaymentRecord, String>,
    PaymentStore {

  @Override
  @Transactional
  public void save(Payment payment) {
    try {
      PaymentRecord paymentRecord = new PaymentRecord();
      paymentRecord.paymentId = payment.paymentId();
      paymentRecord.orderId = payment.orderId();
      paymentRecord.amount = payment.amount();
      paymentRecord.status = payment.status();
      paymentRecord.createdAt = payment.createdAt().toEpochMilli();
      persist(paymentRecord);
    } catch (RuntimeException exception) {
      throw new InfrastructureException("PAYMENT_STORAGE_FAILURE", "Could not save the payment",
          exception);
    }
  }

  @Override
  @Transactional
  public Payment findPayment(String paymentId) {
    try {
      PaymentRecord paymentRecord = findById(paymentId);
      return paymentRecord == null ? null
          : new Payment(paymentRecord.paymentId, paymentRecord.orderId, paymentRecord.amount,
              paymentRecord.status, Instant.ofEpochMilli(paymentRecord.createdAt));
    } catch (RuntimeException exception) {
      throw new InfrastructureException("PAYMENT_STORAGE_FAILURE",
          "Could not retrieve the payment", exception);
    }
  }
}
