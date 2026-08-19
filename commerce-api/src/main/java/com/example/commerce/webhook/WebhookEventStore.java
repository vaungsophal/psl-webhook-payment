package com.example.commerce.webhook;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class WebhookEventStore {

    private final Map<String, WebhookEventView> events = new ConcurrentHashMap<>();

    public StoreResult store(String eventId, String eventType, String payload) {
        WebhookEventView event = new WebhookEventView(eventId, eventType, "RECEIVED", payload, Instant.now());
        WebhookEventView existing = events.putIfAbsent(eventId, event);
        return existing == null ? StoreResult.created(event) : StoreResult.duplicate(existing);
    }

    public Collection<WebhookEventView> findAll() {
        return events.values().stream()
                .sorted((left, right) -> right.receivedAt().compareTo(left.receivedAt()))
                .toList();
    }

    public Optional<WebhookEventView> findById(String eventId) {
        return Optional.ofNullable(events.get(eventId));
    }

    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("received", events.size());
        stats.put("events", findAll());
        return stats;
    }

    public record WebhookEventView(
            String providerEventId,
            String eventType,
            String status,
            String payload,
            Instant receivedAt) {
    }

    public record StoreResult(boolean duplicate, WebhookEventView event) {
        static StoreResult created(WebhookEventView event) {
            return new StoreResult(false, event);
        }

        static StoreResult duplicate(WebhookEventView event) {
            return new StoreResult(true, event);
        }
    }
}
