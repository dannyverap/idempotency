package com.wrda.infrastructure.orders.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class OrderRecord extends PanacheEntityBase {

    @Id
    @Column(name = "order_id", length = 36)
    public String orderId;

    @Column(name = "customer_id", nullable = false, length = 100)
    public String customerId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    public java.math.BigDecimal amount;

    @Column(name = "status", nullable = false, length = 20)
    public String status;

    @Column(name = "created_at", nullable = false)
    public long createdAt;
}
