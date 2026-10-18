package com.wrda.domain;

public class InvalidOrderException extends DomainException {

    public InvalidOrderException() {
        super("INVALID_ORDER", "customerId is required and amount must be greater than zero");
    }
}
