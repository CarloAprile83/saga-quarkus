package org.saga_quarkus.stock.service;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.saga_quarkus.common.data.entity.Order;
import org.saga_quarkus.common.data.entity.StockReservation;
import org.saga_quarkus.common.kafka.DebeziumEventDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Random; // For simulating stock availability

@ApplicationScoped
public class StockProcessor {

    private static final Logger log = LoggerFactory.getLogger(StockProcessor.class);
    private static final Random random = new Random(); // Simulate external stock system

    @Inject
    DebeziumEventDeserializer deserializer;

    @Incoming("order-events") // Matches channel name in application.properties
    @Blocking // Use a worker thread for DB operations and potential external calls
    @Transactional
    public void consumeOrderEvent(String payload) {
        log.debug("Received raw order event: {}", payload);
        Optional<Order> orderOpt = deserializer.deserialize(payload, Order.class);

        if (orderOpt.isEmpty()) {
            log.warn("Could not deserialize order event or 'after' is null. Payload: {}", payload);
            return; // Ignore delete events or malformed messages
        }

        Order order = orderOpt.get();

        // Only process orders that are awaiting stock reservation
        if (Order.STATUS_AWAITING_STOCK.equals(order.status)) {
            log.info("Processing order event for stock reservation: orderId={}, status={}", order.id, order.status);
            reserveStock(order);
        } else {
            log.debug("Ignoring order event for orderId: {} with status: {} (not AWAITING_STOCK)", order.id, order.status);
        }
    }

    private void reserveStock(Order order) {
        // Check if stock reservation already exists for this order to ensure idempotency
        if (StockReservation.count("orderId", order.id) > 0) {
            log.warn("Stock reservation record already exists for orderId: {}. Skipping processing.", order.id);
            return;
        }

        log.info("Attempting stock reservation for orderId: {}, productId: {}, quantity: {}",
                 order.id, order.productId, order.quantity);

        // Simulate checking stock availability (replace with actual logic)
        boolean stockAvailable = simulateStockCheck(order.productId, order.quantity);
        String reservationStatus = stockAvailable ? StockReservation.STATUS_RESERVED : StockReservation.STATUS_FAILED;

        StockReservation reservation = StockReservation.builder()
                .orderId(order.id)
                .productId(order.productId)
                .quantity(order.quantity)
                .status(reservationStatus)
                .build();

        try {
            reservation.persist();
            log.info("Stock reservation record created for orderId: {} with status: {}", order.id, reservationStatus);
        } catch (Exception e) {
            log.error("Failed to persist stock reservation record for orderId: {}", order.id, e);
            // Consider retry or other error handling. If this fails, the saga might stall.
        }
    }

    // --- Simulation Method (Replace with real logic) ---

    private boolean simulateStockCheck(String productId, int quantity) {
        // Simulate that some products might be out of stock or insufficient quantity
        log.debug("Simulating stock check for productId: {}, quantity: {}", productId, quantity);
        // Example: Fail if quantity is > 50 or based on random chance
        if (quantity > 50) {
            log.warn("Simulation: Stock check failed for productId {} due to large quantity {}", productId, quantity);
            return false;
        }
        boolean available = random.nextInt(10) < 9; // 90% availability rate
        log.info("Simulation: Stock check for productId {} result: {}", productId, available);
        return available;
    }
}
