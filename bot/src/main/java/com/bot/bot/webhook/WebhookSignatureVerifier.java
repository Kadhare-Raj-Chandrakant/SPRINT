package com.bot.bot.webhook;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.bot.bot.config.GitHubProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookSignatureVerifier {
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    private final GitHubProperties gitHubProperties;

    public boolean verifySignature(String payload, String signature) {
        try {
            String computedSignature = computeSignature(payload);
            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(
                    computedSignature.getBytes(StandardCharsets.UTF_8),
                    signature != null ? signature.getBytes(StandardCharsets.UTF_8) : new byte[0]
            );
        } catch (Exception e) {
            log.error("Error verifying signature", e);
            return false;
        }
    }

    private String computeSignature(String payload) throws Exception {
        String secret = gitHubProperties.getWebhookSecret();
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("GitHub webhook secret not configured");
        }

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        mac.init(keySpec);

        byte[] result = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return "sha256=" + bytesToHex(result);
    }

    private String bytesToHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = HEX_CHARS[v >>> 4];
            hex[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hex);
    }
}
