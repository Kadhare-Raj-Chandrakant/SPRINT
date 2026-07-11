package com.bot.bot.webhook;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.bot.bot.service.ReviewOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * Webhook controller for GitHub App events.
 * <p>
 * Injects {@code X-GitHub-Delivery} into the MDC "traceId" field for request tracing
 * through async processing pipelines, enabling log correlation across threads.
 */
@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class GitHubWebhookController {

    /** Maximum allowed webhook payload size in bytes (5 MB). */
    private static final long MAX_PAYLOAD_BYTES = 5 * 1024 * 1024L;

    private final WebhookSignatureVerifier signatureVerifier;
    private final ReviewOrchestrator reviewOrchestrator;
    private final Gson gson;

    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-GitHub-Delivery") String deliveryId) throws IOException {

        // Inject trace ID into MDC for the duration of this request
        MDC.put("traceId", deliveryId);
        MDC.put("eventType", eventType);
        try {
            log.info("Received event: {} (delivery: {})", eventType, deliveryId);

            // ── Read raw body bytes ─────────────────────────────
            // Read directly from the input stream to avoid Spring's message converters
            // consuming the body before signature verification (Gson/Jackson may drain
            // the stream trying to deserialize a JSON object into a String).
            byte[] rawBytes = request.getInputStream().readAllBytes();
            String payload = new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8);

            // ── Payload size validation ─────────────────────────
            if (rawBytes.length > MAX_PAYLOAD_BYTES) {
                log.warn("Payload too large: {} bytes (max: {})", rawBytes.length, MAX_PAYLOAD_BYTES);
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body("Payload exceeds maximum size of " + MAX_PAYLOAD_BYTES + " bytes");
            }

            // ── Signature verification ──────────────────────────
            if (signature == null || !signatureVerifier.verifySignature(payload, signature)) {
                log.warn("Invalid or missing webhook signature");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
            }

            // ── Only process pull request events ────────────────
            if (!"pull_request".equals(eventType)) {
                log.debug("Ignoring non-PR event: {}", eventType);
                return ResponseEntity.ok("pong");
            }

            try {
                JsonObject webhookData = gson.fromJson(payload, JsonObject.class);
                if (webhookData == null) {
                    return ResponseEntity.badRequest().body("Invalid JSON payload");
                }

                String action = webhookData.has("action") && !webhookData.get("action").isJsonNull()
                        ? webhookData.get("action").getAsString() : null;

                if (action == null) {
                    log.warn("Missing action in webhook payload");
                    return ResponseEntity.badRequest().body("Missing action field");
                }

                // Enrich MDC with repo context for downstream
                JsonObject repo = webhookData.getAsJsonObject("repository");
                if (repo != null && repo.get("full_name") != null && !repo.get("full_name").isJsonNull()) {
                    MDC.put("repo", repo.get("full_name").getAsString());
                }
                MDC.put("action", action);

                if ("opened".equals(action) || "synchronize".equals(action) || "reopened".equals(action)) {
                    log.info("Processing PR action: {}", action);
                    reviewOrchestrator.processPullRequest(webhookData);
                    return ResponseEntity.accepted().body("Processing started");
                } else {
                    log.debug("Ignoring PR action: {}", action);
                    return ResponseEntity.ok("Action ignored");
                }

            } catch (Exception e) {
                log.error("Error processing webhook", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error processing webhook");
            }

        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
