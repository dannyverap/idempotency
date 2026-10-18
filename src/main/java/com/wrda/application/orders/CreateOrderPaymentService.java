package com.wrda.application.orders;

import com.wrda.domain.Order;
import com.wrda.domain.OrderPayment;
import com.wrda.domain.Payment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CreateOrderPaymentService {

    private final CreateOrderService createOrderService;
    private final CreatePaymentService createPaymentService;

    @Inject
    public CreateOrderPaymentService(CreateOrderService createOrderService,
                                     CreatePaymentService createPaymentService) {
        this.createOrderService = createOrderService;
        this.createPaymentService = createPaymentService;
    }

    @Transactional
    public OrderPayment create(CreateOrderPaymentCommand command) {
        Order order = createOrderService.create(
                new CreateOrderCommand(command.customerId(), command.amount(), false));
        Payment payment = createPaymentService.createForOrder(order.orderId(), command.amount(),
                command.simulatePaymentFailure());
        return new OrderPayment(order, payment);
    }
}
