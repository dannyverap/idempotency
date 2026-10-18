package com.wrda.application.orders;

import java.math.BigDecimal;

public record CreateOrderPaymentCommand(String customerId, BigDecimal amount,
                                        boolean simulatePaymentFailure) {
}
