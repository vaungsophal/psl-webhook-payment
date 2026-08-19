package com.example.commerce.webhook;

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
    private final ObjectMapper objectMapper;

    public WebhookController(
            SignatureVerifier signatureVerifier,
            WebhookEventStore eventStore,
            ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.eventStore = eventStore;
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

        return ResponseEntity.ok(Map.of("status", "accepted", "eventId", eventId, "eventType", eventType));
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
}
