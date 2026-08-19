package com.example.commerce.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class SignatureVerifierTest {

    private static final String SECRET = "test-secret";
    private static final Instant NOW = Instant.parse("2026-08-19T10:30:00Z");

    @Test
    void acceptsValidSignature() throws Exception {
        byte[] body = "{\"eventId\":\"evt_123\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(NOW.getEpochSecond());
        SignatureVerifier verifier = verifier();

        assertThat(verifier.isValid(timestamp, "sha256=" + sign(timestamp, body), body)).isTrue();
    }

    @Test
    void rejectsInvalidSignature() {
        byte[] body = "{\"eventId\":\"evt_123\"}".getBytes(StandardCharsets.UTF_8);
        SignatureVerifier verifier = verifier();

        assertThat(verifier.isValid(String.valueOf(NOW.getEpochSecond()), "sha256=bad", body)).isFalse();
    }

    @Test
    void rejectsExpiredTimestamp() throws Exception {
        byte[] body = "{\"eventId\":\"evt_123\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(NOW.minusSeconds(600).getEpochSecond());
        SignatureVerifier verifier = verifier();

        assertThat(verifier.isValid(timestamp, "sha256=" + sign(timestamp, body), body)).isFalse();
    }

    private SignatureVerifier verifier() {
        return new SignatureVerifier(
                new WebhookProperties(SECRET, 300),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private String sign(String timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + new String(body, StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
