package com.example.commerce.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "webhook")
public record WebhookProperties(String secret, long maxSkewSeconds) {
}
