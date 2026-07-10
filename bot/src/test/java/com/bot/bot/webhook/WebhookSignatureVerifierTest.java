package com.bot.bot.webhook;

import com.bot.bot.config.GitHubProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignatureVerifierTest {

    @Test
    void verifySignatureReturnsTrueForValidSignature() throws Exception {
        GitHubProperties properties = new GitHubProperties();
        properties.setWebhookSecret("secret");
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(properties);

        String payload = "{\"test\":\"value\"}";
        String signature = computeSignature("secret", payload);

        assertTrue(verifier.verifySignature(payload, signature));
    }

    @Test
    void verifySignatureReturnsFalseForInvalidSignature() throws Exception {
        GitHubProperties properties = new GitHubProperties();
        properties.setWebhookSecret("secret");
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(properties);

        String payload = "{\"test\":\"value\"}";
        String signature = computeSignature("secret", payload) + "invalid";

        assertFalse(verifier.verifySignature(payload, signature));
    }

    @Test
    void verifySignatureReturnsFalseWhenSecretMissing() {
        GitHubProperties properties = new GitHubProperties();
        properties.setWebhookSecret(null);
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(properties);

        String payload = "{\"test\":\"value\"}";

        assertFalse(verifier.verifySignature(payload, "sha256=abc"));
    }

    private String computeSignature(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                0,
                secret.length(),
                "HmacSHA256"
        );
        mac.init(keySpec);
        byte[] result = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : result) {
            sb.append(String.format("%02x", b));
        }
        return "sha256=" + sb;
    }
}

