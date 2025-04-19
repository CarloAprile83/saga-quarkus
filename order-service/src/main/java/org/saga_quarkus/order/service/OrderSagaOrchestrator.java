package org.saga_quarkus.order.service;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.saga_quarkus.common.data.entity.Order;
import org.saga_quarkus.common.data.entity.Payment;
import org.saga_quarkus.common.data.entity.StockReservation;
import org.saga_quarkus.common.kafka.DebeziumEventDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@ApplicationScoped
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    @Inject
    DebeziumEventDeserializer deserializer;

    @Incoming("payment-events") // Matches channel name in application.properties
    @Blocking // Use a worker thread as processing involves DB operations
    @Transactional // Manage transaction for DB updates
    public void consumePaymentEvent(String payload) {
        log.debug("Received raw payment event: {}", payload);
        Optional<Payment> paymentOpt = deserializer.deserialize(payload, Payment.class);

        if (paymentOpt.isEmpty()) {
            log.warn("Could not deserialize payment event or 'after' is null. Payload: {}", payload);
            return; // Ignore delete events or malformed messages for now
        }

        Payment payment = paymentOpt.get();
        log.info("Processing payment event for orderId: {}, status: {}", payment.orderId, payment.status);

        Order order = Order.findById(payment.orderId);
        if (order == null) {
            log.error("Order not found for payment event! OrderId: {}", payment.orderId);
            // Consider error handling: dead-letter queue, logging, etc.
            return;
        }

        // --- Saga Logic based on Payment Status ---
        switch (payment.status) {
            case Payment.STATUS_COMPLETED:
                // Payment successful, move order to AWAITING_STOCK
                if (Order.STATUS_PENDING.equals(order.status)) {
                    log.info("Payment completed for Order {}. Updating status to {}.", order.id, Order.STATUS_AWAITING_STOCK);
                    order.updateStatus(Order.STATUS_AWAITING_STOCK);
                } else {
                    log.warn("Received completed payment for Order {} which is not in PENDING state (current: {}). Ignoring.", order.id, order.status);
                }
                break;
            case Payment.STATUS_FAILED:
                // Payment failed, mark order as FAILED
                log.warn("Payment failed for Order {}. Updating status to {}.", order.id, Order.STATUS_FAILED);
                order.updateStatus(Order.STATUS_FAILED);
                // No compensation needed yet as stock wasn't reserved
                break;
            case Payment.STATUS_CANCELLED:
                // Payment cancelled (compensation), potentially mark order as FAILED if not already
                 if (!Order.STATUS_FAILED.equals(order.status) && !Order.STATUS_COMPLETED.equals(order.status)) { // Avoid overriding final states
                    log.info("Payment compensation (cancelled) received for Order {}. Marking as FAILED.", order.id);
                    order.updateStatus(Order.STATUS_FAILED);
                } else {
                     log.info("Payment compensation received for Order {} but status is already {}. Ignoring.", order.id, order.status);
                 }
                break;
            default:
                log.warn("Received unhandled payment status '{}' for orderId: {}", payment.status, payment.orderId);
                break;
        }
    }

    @Incoming("stock-events") // Matches channel name in application.properties
    @Blocking
    @Transactional
    public void consumeStockEvent(String payload) {
        log.debug("Received raw stock event: {}", payload);
        Optional<StockReservation> stockOpt = deserializer.deserialize(payload, StockReservation.class);

        if (stockOpt.isEmpty()) {
            log.warn("Could not deserialize stock event or 'after' is null. Payload: {}", payload);
            return; // Ignore delete events or malformed messages
        }

        StockReservation stock = stockOpt.get();
        log.info("Processing stock event for orderId: {}, status: {}", stock.orderId, stock.status);

        Order order = Order.findById(stock.orderId);
        if (order == null) {
            log.error("Order not found for stock event! OrderId: {}", stock.orderId);
            return;
        }

        // --- Saga Logic based on Stock Status ---
        switch (stock.status) {
            case StockReservation.STATUS_RESERVED:
                // Stock reserved successfully, complete the order
                if (Order.STATUS_AWAITING_STOCK.equals(order.status)) {
                    log.info("Stock reserved for Order {}. Updating status to {}.", order.id, Order.STATUS_COMPLETED);
                    order.updateStatus(Order.STATUS_COMPLETED);
                } else {
                    log.warn("Received stock reservation for Order {} which is not in AWAITING_STOCK state (current: {}). Ignoring.", order.id, order.status);
                    // Potentially needs compensation if payment already happened but order state is wrong
                }
                break;
            case StockReservation.STATUS_FAILED:
                // Stock reservation failed, initiate payment compensation
                if (Order.STATUS_AWAITING_STOCK.equals(order.status)) {
                    log.warn("Stock reservation failed for Order {}. Updating status to {}.", order.id, Order.STATUS_COMPENSATING_PAYMENT);
                    order.updateStatus(Order.STATUS_COMPENSATING_PAYMENT);
                    // The payment-service will listen for this status change via CDC on orders table
                } else {
                     log.warn("Received failed stock reservation for Order {} which is not in AWAITING_STOCK state (current: {}). Ignoring compensation trigger.", order.id, order.status);
                }
                break;
             case StockReservation.STATUS_CANCELLED:
                 // Stock cancelled (compensation), potentially mark order as FAILED if not already
                 if (!Order.STATUS_FAILED.equals(order.status) && !Order.STATUS_COMPLETED.equals(order.status)) { // Avoid overriding final states
                    log.info("Stock compensation (cancelled) received for Order {}. Marking as FAILED.", order.id);
                    order.updateStatus(Order.STATUS_FAILED);
                } else {
                     log.info("Stock compensation received for Order {} but status is already {}. Ignoring.", order.id, order.status);
                 }
                break;
            default:
                log.warn("Received unhandled stock status '{}' for orderId: {}", stock.status, stock.orderId);
                break;
        }
    }
}
