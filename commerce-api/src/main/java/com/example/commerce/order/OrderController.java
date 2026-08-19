package com.example.commerce.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final CommerceStore commerceStore;

    public OrderController(CommerceStore commerceStore) {
        this.commerceStore = commerceStore;
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        CommerceStore.CreateOrderResult result = commerceStore.createOrder(
                request.orderId(),
                request.amount(),
                request.currency());

        if (result.duplicate()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "duplicate_order_id",
                    "message", "Order ID already exists",
                    "order", result.order()));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "created",
                "order", result.order()));
    }

    @GetMapping("/orders")
    public Map<String, Object> listOrders() {
        return Map.of("orders", commerceStore.findOrders());
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<CommerceStore.OrderView> getOrder(@PathVariable String orderId) {
        return commerceStore.findOrder(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/orders/{orderId}/payments")
    public ResponseEntity<Map<String, Object>> registerPayment(
            @PathVariable String orderId,
            @Valid @RequestBody RegisterPaymentRequest request) {
        CommerceStore.RegisterPaymentResult result = commerceStore.registerPayment(
                orderId,
                request.providerPaymentId(),
                request.amount(),
                request.currency());

        return switch (result.status()) {
            case "created" -> ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", result.status(),
                    "payment", result.payment()));
            case "missing_order" -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", result.status(),
                    "message", "Order ID does not exist"));
            default -> ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", result.status(),
                    "message", "Payment is a duplicate or this order already has a payment",
                    "payment", result.payment()));
        };
    }

    @GetMapping("/payments")
    public Map<String, Object> listPayments() {
        return Map.of("payments", commerceStore.findPayments());
    }

    public record CreateOrderRequest(
            @NotBlank String orderId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String currency) {
    }

    public record RegisterPaymentRequest(
            @NotBlank String providerPaymentId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String currency) {
    }
}
