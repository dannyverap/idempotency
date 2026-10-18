package com.wrda.application.orders;

import com.wrda.application.ApplicationException;

public class OrderNotFoundException extends ApplicationException {

    public OrderNotFoundException(String orderId) {
        super("ORDER_NOT_FOUND", "Order not found: " + orderId);
    }
}
