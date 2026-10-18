package com.wrda.application.orders;

import com.wrda.application.orders.port.out.OrderStore;
import com.wrda.domain.Order;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class GetOrderService {

    private final OrderStore orderStore;

    @Inject
    public GetOrderService(OrderStore orderStore) {
        this.orderStore = orderStore;
    }

    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orderStore.findOrder(orderId));
    }
}
