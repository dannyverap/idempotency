package com.wrda.application.orders;

import com.wrda.domain.Order;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import com.wrda.application.orders.port.out.OrderStore;

@ApplicationScoped
public class CreateOrderService {

    private final OrderStore orderStore;

    @Inject
    public CreateOrderService(OrderStore orderStore) {
        this.orderStore = orderStore;
    }

    public Order create(CreateOrderCommand command) {
        // Retardo de 3 a 5 s para poder probar la concurrencia a mano.
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(3000, 5001));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OrderProcessingException();
        }

        Order order = Order.create(UUID.randomUUID().toString(), command.customerId(), command.amount(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS));
        if (command.simulateFailure()) {
            throw new OrderProcessingException();
        }
        orderStore.save(order);
        return order;
    }
}
