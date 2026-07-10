package com.bot.bot.github;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.bot.bot.config.GitHubProperties;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubJwtGenerator {
    private static final int TOKEN_EXPIRY_SECONDS = 600;
    private static final int CACHE_BUFFER_SECONDS = 60;

    private final GitHubProperties gitHubProperties;
    private volatile PrivateKey privateKey;
    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    /**
     * Generates a JWT token for GitHub App authentication.
     * Tokens are cached until 60 seconds before expiry to handle clock skew.
     */
    public String generateAppToken() {
        if (Instant.now().isBefore(tokenExpiry.minusSeconds(CACHE_BUFFER_SECONDS))
                && cachedToken != null) {
            return cachedToken;
        }

        synchronized (this) {
            // Double-check after acquiring lock
            if (Instant.now().isBefore(tokenExpiry.minusSeconds(CACHE_BUFFER_SECONDS))
                    && cachedToken != null) {
                return cachedToken;
            }

            try {
                PrivateKey key = getPrivateKey();
                Instant now = Instant.now();
                Instant expiration = now.plusSeconds(TOKEN_EXPIRY_SECONDS);

                String token = Jwts.builder()
                        .setIssuedAt(Date.from(now))
                        .setExpiration(Date.from(expiration))
                        .claim("iss", gitHubProperties.getAppId())
                        .signWith(key, SignatureAlgorithm.RS256)
                        .compact();

                cachedToken = token;
                tokenExpiry = expiration;
                return token;
            } catch (Exception e) {
                log.error("Error generating JWT token", e);
                throw new RuntimeException("Failed to generate JWT token", e);
            }
        }
    }

    private PrivateKey getPrivateKey() throws Exception {
        if (privateKey == null) {
            synchronized (this) {
                if (privateKey == null) {
                    privateKey = loadPrivateKey(gitHubProperties.getPrivateKeyPath());
                }
            }
        }
        return privateKey;
    }

    private PrivateKey loadPrivateKey(String path) throws Exception {
        String pem = new String(Files.readAllBytes(Paths.get(path)));
        log.info("Loading private key from: {}", path);

        if (pem.contains("-----BEGIN RSA PRIVATE KEY-----")) {
            log.debug("Detected PKCS#1 format private key");
            return readPkcs1PrivateKey(pem);
        } else if (pem.contains("-----BEGIN PRIVATE KEY-----")) {
            log.debug("Detected PKCS#8 format private key");
            return readPkcs8PrivateKey(pem);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported private key format. Expected PEM header: "
                    + "-----BEGIN RSA PRIVATE KEY----- or -----BEGIN PRIVATE KEY-----");
        }
    }

    private PrivateKey readPkcs8PrivateKey(String pem) throws Exception {
        String b64 = extractBase64(pem, "-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----");
        byte[] der = Base64.getDecoder().decode(b64);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private PrivateKey readPkcs1PrivateKey(String pem) throws Exception {
        String b64 = extractBase64(pem, "-----BEGIN RSA PRIVATE KEY-----", "-----END RSA PRIVATE KEY-----");
        byte[] der = Base64.getDecoder().decode(b64);
        // Wrap PKCS#1 DER in PKCS#8 container to use standard KeyFactory
        byte[] pkcs8Der = wrapPkcs1InPkcs8(der);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8Der);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private static String extractBase64(String pem, String beginMarker, String endMarker) {
        return pem.replace(beginMarker, "")
                  .replace(endMarker, "")
                  .replaceAll("\\s+", "");
    }

    /**
     * Wraps a PKCS#1 DER-encoded RSA private key in a PKCS#8 container.
     * This avoids needing BouncyCastle or manual ASN.1 parsing of key components.
     */
    static byte[] wrapPkcs1InPkcs8(byte[] pkcs1Der) throws IOException {
        // AlgorithmIdentifier for rsaEncryption (OID 1.2.840.113549.1.1.1)
        byte[] algorithmId = {
            (byte) 0x30, (byte) 0x0d,                             // SEQUENCE (13 bytes)
            (byte) 0x06, (byte) 0x09, (byte) 0x2a, (byte) 0x86,
            (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d,  // OID rsaEncryption
                    (byte) 0x01, (byte) 0x01, (byte) 0x01,
            (byte) 0x05, (byte) 0x00                              // NULL
        };

        // Version INTEGER 0
        byte[] version = { (byte) 0x02, (byte) 0x01, (byte) 0x00 };

        // Wrap PKCS#1 key in OCTET STRING
        byte[] wrappedKey = encodeDerTag(0x04, pkcs1Der);

        // Combine into PKCS#8 private key SEQUENCE
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        inner.write(version);
        inner.write(algorithmId);
        inner.write(wrappedKey);

        return encodeDerTag(0x30, inner.toByteArray());
    }

    private static byte[] encodeDerTag(int tag, byte[] content) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(tag);
        writeDerLength(bos, content.length);
        bos.write(content);
        return bos.toByteArray();
    }

    private static void writeDerLength(ByteArrayOutputStream bos, int length) throws IOException {
        if (length < 128) {
            bos.write(length);
        } else {
            int numBytes = (Integer.SIZE - Integer.numberOfLeadingZeros(length) + 7) / 8;
            bos.write(0x80 | numBytes);
            for (int i = numBytes - 1; i >= 0; i--) {
                bos.write((length >> (i * 8)) & 0xFF);
            }
        }
    }
}
