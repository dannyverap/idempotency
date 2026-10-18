package com.wrda.application.orders;

import com.wrda.application.ApplicationException;

public class OrderProcessingException extends ApplicationException {

    public OrderProcessingException() {
        super("ORDER_PROCESSING_FAILED", "The order could not be processed");
    }
}
