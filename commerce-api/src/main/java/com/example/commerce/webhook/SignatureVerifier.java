package com.example.commerce.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class SignatureVerifier {

    private final WebhookProperties properties;
    private final Clock clock;

    public SignatureVerifier(WebhookProperties properties) {
        this(properties, Clock.systemUTC());
    }

    SignatureVerifier(WebhookProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean isValid(String timestampHeader, String signatureHeader, byte[] rawBody) {
        if (timestampHeader == null || signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException ex) {
            return false;
        }

        long now = Instant.now(clock).getEpochSecond();
        if (Math.abs(now - timestamp) > properties.maxSkewSeconds()) {
            return false;
        }

        String expected = "sha256=" + hmac(timestampHeader + "." + new String(rawBody, StandardCharsets.UTF_8));
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(String signedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Unable to calculate webhook signature", ex);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
