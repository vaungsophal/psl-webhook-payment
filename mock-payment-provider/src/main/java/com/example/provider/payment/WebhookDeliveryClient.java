package com.example.provider.payment;

import com.example.provider.payment.PaymentController.PaymentRecord;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WebhookDeliveryClient {

    private final ProviderProperties properties;
    private final RestClient restClient;

    public WebhookDeliveryClient(ProviderProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    public DeliveryResult sendPaymentCompleted(PaymentRecord payment) {
        String eventId = "evt_" + UUID.randomUUID();
        String body = """
                {
                  "eventId": "%s",
                  "eventType": "payment.completed",
                  "createdAt": "%s",
                  "data": {
                    "paymentId": "%s",
                    "orderId": "%s",
                    "amount": %s,
                    "currency": "%s",
                    "status": "COMPLETED"
                  }
                }
                """.formatted(
                eventId,
                Instant.now(),
                payment.paymentId(),
                payment.orderId(),
                payment.amount(),
                payment.currency());

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = "sha256=" + hmac(timestamp + "." + body);

        return restClient.post()
                .uri(properties.commerceWebhookUrl())
                .header("X-Webhook-Id", eventId)
                .header("X-Webhook-Timestamp", timestamp)
                .header("X-Webhook-Signature", signature)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .exchange((request, response) -> new DeliveryResult(
                        response.getStatusCode().value(),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private String hmac(String signedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Unable to sign webhook", ex);
        }
    }

    public record DeliveryResult(int statusCode, String body) {
    }
}
