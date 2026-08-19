package com.example.provider.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "provider")
public record ProviderProperties(String webhookSecret, String commerceWebhookUrl) {
}
