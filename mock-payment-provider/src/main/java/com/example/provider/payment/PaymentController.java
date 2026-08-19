package com.example.provider.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mock-payments")
public class PaymentController {

    private final Map<String, PaymentRecord> payments = new ConcurrentHashMap<>();
    private final WebhookDeliveryClient webhookDeliveryClient;

    public PaymentController(WebhookDeliveryClient webhookDeliveryClient) {
        this.webhookDeliveryClient = webhookDeliveryClient;
    }

    @PostMapping
    public Map<String, Object> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        String paymentId = "pay_" + UUID.randomUUID();
        PaymentRecord payment = new PaymentRecord(
                paymentId,
                request.orderId(),
                request.amount(),
                request.currency(),
                "PENDING",
                Instant.now());
        payments.put(paymentId, payment);

        return Map.of(
                "paymentId", paymentId,
                "status", payment.status(),
                "checkoutUrl", "/api/mock-payments/" + paymentId + "/complete");
    }

    @PostMapping("/{paymentId}/complete")
    public ResponseEntity<Map<String, Object>> completePayment(@PathVariable String paymentId) {
        PaymentRecord payment = payments.get(paymentId);
        if (payment == null) {
            return ResponseEntity.notFound().build();
        }

        PaymentRecord completed = payment.withStatus("COMPLETED");
        payments.put(paymentId, completed);
        WebhookDeliveryClient.DeliveryResult result = webhookDeliveryClient.sendPaymentCompleted(completed);

        return ResponseEntity.status(result.statusCode()).body(Map.of(
                "paymentId", paymentId,
                "status", completed.status(),
                "webhookStatusCode", result.statusCode(),
                "webhookResponse", result.body()));
    }

    @GetMapping
    public Map<String, Object> listPayments() {
        return Map.of("payments", payments.values());
    }

    public record CreatePaymentRequest(
            @NotBlank String orderId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String currency) {
    }

    public record PaymentRecord(
            String paymentId,
            String orderId,
            BigDecimal amount,
            String currency,
            String status,
            Instant createdAt) {

        PaymentRecord withStatus(String newStatus) {
            return new PaymentRecord(paymentId, orderId, amount, currency, newStatus, createdAt);
        }
    }
}
