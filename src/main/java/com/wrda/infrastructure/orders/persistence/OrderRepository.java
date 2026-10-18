package com.wrda.infrastructure.orders.persistence;

import com.wrda.application.orders.port.out.OrderStore;
import com.wrda.domain.Order;
import com.wrda.infrastructure.error.InfrastructureException;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;

@ApplicationScoped
public class OrderRepository implements PanacheRepositoryBase<OrderRecord, String>, OrderStore {

  @Override
  @Transactional
  public void save(Order order) {
    try {
      OrderRecord orderRecord = new OrderRecord();
      orderRecord.orderId = order.orderId();
      orderRecord.customerId = order.customerId();
      orderRecord.amount = order.amount();
      orderRecord.status = order.status();
      orderRecord.createdAt = order.createdAt().toEpochMilli();
      persist(orderRecord);
    } catch (RuntimeException exception) {
      throw new InfrastructureException("ORDER_STORAGE_FAILURE", "Could not save the order",
          exception);
    }
  }

  @Override
  @Transactional
  public Order findOrder(String orderId) {
    try {
      OrderRecord orderRecord = findById(orderId);
      return orderRecord == null ? null
          : new Order(orderRecord.orderId, orderRecord.customerId, orderRecord.amount,
              orderRecord.status, Instant.ofEpochMilli(orderRecord.createdAt));
    } catch (RuntimeException exception) {
      throw new InfrastructureException("ORDER_STORAGE_FAILURE", "Could not retrieve the order",
          exception);
    }
  }
}
