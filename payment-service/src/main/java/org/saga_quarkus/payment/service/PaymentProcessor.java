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

    @Incoming("order-events")
    @Blocking
    public void consumeOrderEvent(String payload) {
        log.debug("Received raw order event: {}", payload);
        if (payload == null || payload.isEmpty()) {
            log.warn("Payload is null or empty!");
            return;
        }
        Optional<Order> orderOpt = deserializer.deserialize(payload, Order.class);

        if (orderOpt.isEmpty()) {
            log.warn("Could not deserialize order event or 'after' is null. Payload: {}", payload);
            return;
        }

        Order order = orderOpt.get();
        log.info("Processing order event for orderId: {}, status: {}", order.id, order.status);

        switch (order.status) {
            case Order.STATUS_PENDING:
                // Simula pagamento FUORI dalla transazione
                boolean paymentSuccess = simulatePaymentGateway();
                processPayment(order, paymentSuccess);
                break;
            case Order.STATUS_COMPENSATING_PAYMENT:
                // Compensazione pagamento FUORI dalla transazione
                compensatePayment(order);
                break;
            default:
                log.debug("Ignoring order event for orderId: {} with status: {}", order.id, order.status);
                break;
        }
    }

    @Transactional
    void processPayment(Order order, boolean paymentSuccess) {
        if (Payment.count("orderId", order.id) > 0) {
            log.warn("Payment record already exists for orderId: {}. Skipping processing.", order.id);
            return;
        }
        String paymentStatus = paymentSuccess ? Payment.STATUS_COMPLETED : Payment.STATUS_FAILED;
        BigDecimal amount = BigDecimal.valueOf(order.quantity * 10.0);
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
        }
    }

    void compensatePayment(Order order) {
        Payment existingPayment = Payment.find("orderId", order.id).firstResult();
        if (existingPayment == null) {
            log.warn("Compensation requested for orderId: {}, but no existing payment found. Ignoring.", order.id);
            return;
        }
        if (Payment.STATUS_CANCELLED.equals(existingPayment.status) || Payment.STATUS_FAILED.equals(existingPayment.status)) {
            log.warn("Compensation requested for orderId: {}, but payment status is already '{}'. Ignoring.", order.id, existingPayment.status);
            return;
        }
        log.info("Attempting payment compensation (cancellation/refund) for orderId: {}", order.id);
        boolean compensationSuccess = simulateRefundGateway();
        if (compensationSuccess) {
            existingPayment.status = Payment.STATUS_CANCELLED;
            try {
                existingPayment.persist();
                log.info("Payment compensation successful for orderId: {}. Status updated to CANCELLED.", order.id);
            } catch (Exception e) {
                log.error("Failed to update payment status to CANCELLED for orderId: {}", order.id, e);
            }
        } else {
            log.error("Payment compensation FAILED for orderId: {}. Manual intervention required!", order.id);
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
