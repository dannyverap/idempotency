package com.wrda.infrastructure.orders.http;

import com.wrda.domain.Order;
import com.wrda.domain.OrderPayment;
import com.wrda.domain.Payment;
import com.wrda.infrastructure.orders.contract.model.OrderPaymentResponse;
import com.wrda.infrastructure.orders.contract.model.OrderResponse;
import com.wrda.infrastructure.orders.contract.model.PaymentResponse;

import java.util.Date;
import java.util.UUID;

public final class OrderApiMapper {

    private OrderApiMapper() {
    }

    public static OrderResponse toOrderResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(UUID.fromString(order.orderId()));
        response.setCustomerId(order.customerId());
        response.setAmount(order.amount().stripTrailingZeros());
        response.setStatus(OrderResponse.StatusEnum.CREATED);
        response.setCreatedAt(Date.from(order.createdAt()));
        return response;
    }

    public static PaymentResponse toPaymentResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(UUID.fromString(payment.paymentId()));
        response.setOrderId(UUID.fromString(payment.orderId()));
        response.setAmount(payment.amount().stripTrailingZeros());
        response.setStatus(PaymentResponse.StatusEnum.SUCCEEDED);
        response.setCreatedAt(Date.from(payment.createdAt()));
        return response;
    }

    public static OrderPaymentResponse toOrderPaymentResponse(OrderPayment orderPayment) {
        OrderPaymentResponse response = new OrderPaymentResponse();
        response.setOrder(toOrderResponse(orderPayment.order()));
        response.setPayment(toPaymentResponse(orderPayment.payment()));
        return response;
    }
}
