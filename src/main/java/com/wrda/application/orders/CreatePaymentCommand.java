package com.wrda.application.orders;

import java.math.BigDecimal;

public record CreatePaymentCommand(String orderId, BigDecimal amount, boolean simulateFailure) {
}
