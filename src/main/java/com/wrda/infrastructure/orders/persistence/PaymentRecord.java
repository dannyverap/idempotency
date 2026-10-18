package com.wrda.infrastructure.orders.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "payments")
public class PaymentRecord extends PanacheEntityBase {

    @Id
    @Column(name = "payment_id", length = 36)
    public String paymentId;

    @Column(name = "order_id", nullable = false, length = 36)
    public String orderId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    public BigDecimal amount;

    @Column(name = "status", nullable = false, length = 20)
    public String status;

    @Column(name = "created_at", nullable = false)
    public long createdAt;
}
