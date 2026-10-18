package com.wrda.application.orders;

import java.math.BigDecimal;

public record CreateOrderCommand(String customerId, BigDecimal amount, boolean simulateFailure) {
}
