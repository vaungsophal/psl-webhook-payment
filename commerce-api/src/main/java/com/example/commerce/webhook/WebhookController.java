package com.example.commerce.webhook;

import com.example.commerce.order.CommerceStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WebhookController {

    private final SignatureVerifier signatureVerifier;
    private final WebhookEventStore eventStore;
    private final CommerceStore commerceStore;
    private final ObjectMapper objectMapper;

    public WebhookController(
            SignatureVerifier signatureVerifier,
            WebhookEventStore eventStore,
            CommerceStore commerceStore,
            ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.eventStore = eventStore;
        this.commerceStore = commerceStore;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhooks/mock-payments")
    public ResponseEntity<Map<String, Object>> receiveMockPaymentWebhook(
            @RequestHeader("X-Webhook-Timestamp") String timestamp,
            @RequestHeader("X-Webhook-Signature") String signature,
            @RequestHeader(value = "X-Webhook-Id", required = false) String webhookId,
            @RequestBody byte[] rawBody) throws IOException {

        if (!signatureVerifier.isValid(timestamp, signature, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "rejected"));
        }

        JsonNode payload = objectMapper.readTree(rawBody);
        String eventId = firstText(webhookId, payload.path("eventId").asText(null));
        if (eventId == null || eventId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "missing_event_id"));
        }

        String eventType = payload.path("eventType").asText("unknown");
        WebhookEventStore.StoreResult result = eventStore.store(
                eventId,
                eventType,
                new String(rawBody, StandardCharsets.UTF_8));

        if (result.duplicate()) {
            return ResponseEntity.ok(Map.of("status", "duplicate", "eventId", eventId));
        }

        CommerceStore.WebhookPaymentResult paymentResult = applyPaymentEvent(eventType, payload);

        return ResponseEntity.ok(Map.of(
                "status", "accepted",
                "eventId", eventId,
                "eventType", eventType,
                "paymentResult", paymentResult.status(),
                "paymentReason", paymentResult.reason() == null ? "" : paymentResult.reason()));
    }

    @GetMapping("/admin/webhook-events")
    public Map<String, Object> listWebhookEvents() {
        return eventStore.stats();
    }

    @GetMapping("/admin/webhook-events/{eventId}")
    public ResponseEntity<WebhookEventStore.WebhookEventView> getWebhookEvent(@PathVariable String eventId) {
        return eventStore.findById(eventId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private String firstText(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private CommerceStore.WebhookPaymentResult applyPaymentEvent(String eventType, JsonNode payload) {
        if (!"payment.completed".equals(eventType)) {
            return CommerceStore.WebhookPaymentResult.unchanged("unsupported_event_type");
        }

        JsonNode data = payload.path("data");
        String providerPaymentId = data.path("paymentId").asText(null);
        String orderId = data.path("orderId").asText(null);
        String currency = data.path("currency").asText(null);

        if (providerPaymentId == null || orderId == null || currency == null || !data.path("amount").isNumber()) {
            return CommerceStore.WebhookPaymentResult.rejected("invalid_payment_payload");
        }

        return commerceStore.completePaymentFromWebhook(
                providerPaymentId,
                orderId,
                data.path("amount").decimalValue(),
                currency);
    }
}
