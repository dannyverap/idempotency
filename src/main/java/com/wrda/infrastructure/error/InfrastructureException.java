package com.wrda.infrastructure.error;

public class InfrastructureException extends RuntimeException {

    private final String code;

    public InfrastructureException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
