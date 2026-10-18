package com.wrda.application.orders.port.out;

import com.wrda.domain.Order;

public interface OrderStore {

    void save(Order order);

    Order findOrder(String orderId);
}
