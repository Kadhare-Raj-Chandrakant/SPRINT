package com.bot.bot.actions;

import com.bot.bot.config.AppProperties;
import com.bot.bot.persistence.Meta;
import com.bot.bot.persistence.MetaRepository;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signed, expiring, single-use tokens for one-click PR actions.
 * Token = base64url(payload) + "." + base64url(HMAC-SHA256(secret, payload)).
 * A token is consumed (marked used) on the first successful verify.
 */
public class TokenService {
    private static final String USED_PREFIX = "used:";

    private final String secret;
    private final String baseUrl;
    private final long expirySeconds;

    private final MetaRepository metaRepository;

    public TokenService(AppProperties props, MetaRepository metaRepository) {
        this.secret = props.getActionSecret();
        this.baseUrl = props.getBaseUrl();
        this.expirySeconds = 7L * 24 * 60 * 60; // 7 days
        this.metaRepository = metaRepository;
    }

    public record TokenPayload(String owner, String repo, int prNumber, String action, long expiryEpoch) {}

    public String generate(String owner, String repo, int prNumber, String action) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("action-secret not configured");
        }
        long expiry = Instant.now().plusSeconds(expirySeconds).getEpochSecond();
        String payload = String.join("|",
                owner, repo, String.valueOf(prNumber), action, String.valueOf(expiry));
        String b64 = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        String sig = hmac(b64);
        return b64 + "." + sig;
    }

    public String buildActionUrl(String owner, String repo, int prNumber, String action) {
        if (secret == null || secret.isBlank()) {
            return ""; // actions not configured
        }
        String token = generate(owner, repo, prNumber, action);
        String base = baseUrl != null ? baseUrl : "";
        return base + "/action?token=" + token + "&do=" + action;
    }

    public TokenPayload verify(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenException("missing token");
        }
        int sep = token.indexOf('.');
        if (sep < 0) {
            throw new TokenException("malformed token");
        }
        String b64 = token.substring(0, sep);
        String providedSig = token.substring(sep + 1);

        String expectedSig = hmac(b64);
        if (!constantTimeEquals(expectedSig, providedSig)) {
            throw new TokenException("bad signature");
        }
        if (metaRepository.findById(USED_PREFIX + sha256Hex(token)).isPresent()) {
            throw new TokenException("token already used");
        }

        String payload = new String(decodeBase64Url(b64), StandardCharsets.UTF_8);
        String[] parts = payload.split("\\|");
        if (parts.length != 5) {
            throw new TokenException("malformed payload");
        }
        long expiry = Long.parseLong(parts[4]);
        if (Instant.now().getEpochSecond() > expiry) {
            throw new TokenException("token expired");
        }
        metaRepository.save(usedRow(token));

        return new TokenPayload(parts[0], parts[1], Integer.parseInt(parts[2]), parts[3], expiry);
    }

    private Meta usedRow(String token) {
        Meta m = new Meta();
        m.setKey(USED_PREFIX + sha256Hex(token));
        m.setValue("1");
        return m;
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return base64Url(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC init failed", e);
        }
    }

    private String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] raw = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ab.length != bb.length) return false;
        int result = 0;
        for (int i = 0; i < ab.length; i++) {
            result |= ab[i] ^ bb[i];
        }
        return result == 0;
    }

    private static String base64Url(byte[] bytes) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] decodeBase64Url(String s) {
        return java.util.Base64.getUrlDecoder().decode(s);
    }
}
