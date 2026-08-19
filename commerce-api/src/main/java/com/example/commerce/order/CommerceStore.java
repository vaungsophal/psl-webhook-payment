package com.example.commerce.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class CommerceStore {

    private final Map<String, OrderView> orders = new ConcurrentHashMap<>();
    private final Map<String, PaymentView> paymentsByProviderId = new ConcurrentHashMap<>();
    private final Map<String, String> paymentIdByOrderId = new ConcurrentHashMap<>();

    public CreateOrderResult createOrder(String orderId, BigDecimal amount, String currency) {
        OrderView order = new OrderView(orderId, amount, currency, "CREATED", Instant.now(), Instant.now());
        OrderView existing = orders.putIfAbsent(orderId, order);
        return existing == null ? CreateOrderResult.created(order) : CreateOrderResult.duplicate(existing);
    }

    public RegisterPaymentResult registerPayment(
            String orderId,
            String providerPaymentId,
            BigDecimal amount,
            String currency) {
        OrderView order = orders.get(orderId);
        if (order == null) {
            return RegisterPaymentResult.missingOrder();
        }

        String existingPaymentForOrder = paymentIdByOrderId.putIfAbsent(orderId, providerPaymentId);
        if (existingPaymentForOrder != null && !existingPaymentForOrder.equals(providerPaymentId)) {
            return RegisterPaymentResult.orderAlreadyHasPayment(paymentsByProviderId.get(existingPaymentForOrder));
        }

        PaymentView payment = new PaymentView(providerPaymentId, orderId, amount, currency, "PENDING", Instant.now(), Instant.now());
        PaymentView existingPayment = paymentsByProviderId.putIfAbsent(providerPaymentId, payment);
        if (existingPayment != null) {
            return RegisterPaymentResult.duplicatePayment(existingPayment);
        }

        orders.computeIfPresent(orderId, (id, existing) -> existing.withStatus("PAYMENT_PENDING"));
        return RegisterPaymentResult.created(payment);
    }

    public WebhookPaymentResult completePaymentFromWebhook(
            String providerPaymentId,
            String orderId,
            BigDecimal amount,
            String currency) {
        PaymentView payment = paymentsByProviderId.get(providerPaymentId);
        if (payment == null) {
            return WebhookPaymentResult.rejected("unknown_provider_payment_id");
        }
        if (!payment.orderId().equals(orderId)) {
            return WebhookPaymentResult.rejected("order_id_mismatch");
        }
        if (payment.amount().compareTo(amount) != 0) {
            return WebhookPaymentResult.rejected("amount_mismatch");
        }
        if (!payment.currency().equalsIgnoreCase(currency)) {
            return WebhookPaymentResult.rejected("currency_mismatch");
        }
        if ("COMPLETED".equals(payment.status())) {
            return WebhookPaymentResult.unchanged("payment_already_completed");
        }

        PaymentView completedPayment = payment.withStatus("COMPLETED");
        paymentsByProviderId.put(providerPaymentId, completedPayment);
        orders.computeIfPresent(orderId, (id, existing) -> existing.withStatus("PAID"));
        return WebhookPaymentResult.updated(completedPayment);
    }

    public Collection<OrderView> findOrders() {
        return orders.values().stream()
                .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                .toList();
    }

    public Collection<PaymentView> findPayments() {
        return paymentsByProviderId.values().stream()
                .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                .toList();
    }

    public Optional<OrderView> findOrder(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    public record OrderView(
            String orderId,
            BigDecimal amount,
            String currency,
            String status,
            Instant createdAt,
            Instant updatedAt) {

        OrderView withStatus(String newStatus) {
            return new OrderView(orderId, amount, currency, newStatus, createdAt, Instant.now());
        }
    }

    public record PaymentView(
            String providerPaymentId,
            String orderId,
            BigDecimal amount,
            String currency,
            String status,
            Instant createdAt,
            Instant updatedAt) {

        PaymentView withStatus(String newStatus) {
            return new PaymentView(providerPaymentId, orderId, amount, currency, newStatus, createdAt, Instant.now());
        }
    }

    public record CreateOrderResult(boolean duplicate, OrderView order) {
        static CreateOrderResult created(OrderView order) {
            return new CreateOrderResult(false, order);
        }

        static CreateOrderResult duplicate(OrderView order) {
            return new CreateOrderResult(true, order);
        }
    }

    public record RegisterPaymentResult(String status, PaymentView payment) {
        static RegisterPaymentResult created(PaymentView payment) {
            return new RegisterPaymentResult("created", payment);
        }

        static RegisterPaymentResult duplicatePayment(PaymentView payment) {
            return new RegisterPaymentResult("duplicate_provider_payment_id", payment);
        }

        static RegisterPaymentResult orderAlreadyHasPayment(PaymentView payment) {
            return new RegisterPaymentResult("order_already_has_payment", payment);
        }

        static RegisterPaymentResult missingOrder() {
            return new RegisterPaymentResult("missing_order", null);
        }
    }

    public record WebhookPaymentResult(String status, String reason, PaymentView payment) {
        public static WebhookPaymentResult updated(PaymentView payment) {
            return new WebhookPaymentResult("updated", null, payment);
        }

        public static WebhookPaymentResult unchanged(String reason) {
            return new WebhookPaymentResult("unchanged", reason, null);
        }

        public static WebhookPaymentResult rejected(String reason) {
            return new WebhookPaymentResult("rejected", reason, null);
        }
    }
}
