package org.saga_quarkus.payment.service;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.saga_quarkus.common.data.entity.Order;
import org.saga_quarkus.common.data.entity.Payment;
import org.saga_quarkus.common.kafka.DebeziumEventDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Random; // For simulating payment success/failure

@ApplicationScoped
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);
    private static final Random random = new Random(); // Simulate external payment gateway

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
        log.info("Processing order event for orderId: {}, status: {}", order.id, order.status);

        // --- Payment Logic based on Order Status ---
        switch (order.status) {
            case Order.STATUS_PENDING:
                // New order received, attempt to process payment
                processPayment(order);
                break;
            case Order.STATUS_COMPENSATING_PAYMENT:
                // Stock reservation failed, need to compensate (cancel/refund) payment
                compensatePayment(order);
                break;
            // Ignore other statuses like AWAITING_STOCK, COMPLETED, FAILED as payment is already handled or irrelevant
            default:
                log.debug("Ignoring order event for orderId: {} with status: {}", order.id, order.status);
                break;
        }
    }

    private void processPayment(Order order) {
        // Check if payment already exists for this order to ensure idempotency
        if (Payment.count("orderId", order.id) > 0) {
            log.warn("Payment record already exists for orderId: {}. Skipping processing.", order.id);
            return;
        }

        log.info("Attempting payment processing for orderId: {}", order.id);

        // Simulate payment gateway interaction (replace with actual logic)
        boolean paymentSuccess = simulatePaymentGateway();
        String paymentStatus = paymentSuccess ? Payment.STATUS_COMPLETED : Payment.STATUS_FAILED;

        // Simulate calculating amount (replace with actual logic if needed)
        BigDecimal amount = BigDecimal.valueOf(order.quantity * 10.0); // Example: price is 10.0 per item

        Payment payment = Payment.builder()
                .orderId(order.id)
                .amount(amount)
                .status(paymentStatus)
                .build();

        try {
            payment.persist();
            log.info("Payment record created for orderId: {} with status: {}", order.id, paymentStatus);
        } catch (Exception e) {
            log.error("Failed to persist payment record for orderId: {}", order.id, e);
            // Consider retry or other error handling
        }
    }

    private void compensatePayment(Order order) {
        // Find the original payment record(s) for the order
        // Assuming only one payment record per order for simplicity
        Payment existingPayment = Payment.find("orderId", order.id).firstResult();

        if (existingPayment == null) {
            log.warn("Compensation requested for orderId: {}, but no existing payment found. Ignoring.", order.id);
            return; // Nothing to compensate
        }

        // Check if compensation already happened or payment already failed
        if (Payment.STATUS_CANCELLED.equals(existingPayment.status) || Payment.STATUS_FAILED.equals(existingPayment.status)) {
            log.warn("Compensation requested for orderId: {}, but payment status is already '{}'. Ignoring.", order.id, existingPayment.status);
            return;
        }

        log.info("Attempting payment compensation (cancellation/refund) for orderId: {}", order.id);

        // Simulate refund/cancellation logic
        boolean compensationSuccess = simulateRefundGateway(); // Usually should succeed unless technical issues

        if (compensationSuccess) {
            existingPayment.status = Payment.STATUS_CANCELLED;
            try {
                existingPayment.persist(); // Update the existing payment record
                log.info("Payment compensation successful for orderId: {}. Status updated to CANCELLED.", order.id);
            } catch (Exception e) {
                log.error("Failed to update payment status to CANCELLED for orderId: {}", order.id, e);
                // Critical error - needs monitoring/manual intervention
            }
        } else {
            log.error("Payment compensation FAILED for orderId: {}. Manual intervention required!", order.id);
            // This is a critical state - the system couldn't automatically compensate.
            // Mark the payment or order in a specific error state if possible, or rely on monitoring.
        }
    }

    // --- Simulation Methods (Replace with real logic) ---

    private boolean simulatePaymentGateway() {
        // Simulate some failures
        return random.nextInt(10) < 8; // 80% success rate
    }

     private boolean simulateRefundGateway() {
        // Refunds usually succeed unless there's a system issue
        return random.nextInt(100) < 98; // 98% success rate
    }
}
