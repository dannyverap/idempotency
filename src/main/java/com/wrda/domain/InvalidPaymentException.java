package com.wrda.domain;

public class InvalidPaymentException extends DomainException {

    public InvalidPaymentException() {
        super("INVALID_PAYMENT", "orderId is required and amount must be greater than zero");
    }
}
