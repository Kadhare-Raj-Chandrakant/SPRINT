package com.bot.bot.web;

import com.bot.bot.actions.TokenException;
import com.bot.bot.actions.TokenService;
import com.bot.bot.github.GitHubApiClient;
import com.bot.bot.persistence.PrAnalysis;
import com.bot.bot.persistence.PrAnalysisRepository;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One-click PR action handler (SRS §8/§9). Verifies a signed single-use token,
 * maps the action to a GitHub call, and marks the analysis ACTIONED.
 * No action is taken without a valid token; never auto-merges.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ActionController {

    private final TokenService tokenService;
    private final PrAnalysisRepository prAnalysisRepository;
    private final GitHubApiClient gitHubApiClient;

    @GetMapping(value = "/action", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> handleAction(
            @RequestParam("token") String token,
            @RequestParam("do") String action) {

        TokenService.TokenPayload payload;
        try {
            payload = tokenService.verify(token);
        } catch (TokenException e) {
            boolean used = "token already used".equals(e.getMessage());
            return ResponseEntity.status(used ? 410 : 400).body(resultPage("Invalid or expired link", false));
        }

        // The action in the URL must match the action the token was signed for.
        if (action == null || !action.equalsIgnoreCase(payload.action())) {
            return ResponseEntity.status(400).body(resultPage("Action mismatch", false));
        }

        Optional<PrAnalysis> found = prAnalysisRepository.findLatest(
                payload.owner(), payload.repo(), payload.prNumber());
        if (found.isEmpty()) {
            return ResponseEntity.status(404).body(resultPage("PR not found", false));
        }
        PrAnalysis analysis = found.get();
        if (Boolean.TRUE.equals(analysis.getActionTaken())) {
            return ResponseEntity.status(410).body(resultPage("Action already taken", true));
        }

        try {
            long installationId = parseInstallationId(analysis.getInstallationId());
            switch (payload.action().toLowerCase()) {
                case "approve" ->
                        gitHubApiClient.submitReview(payload.owner(), payload.repo(), payload.prNumber(),
                                "Approved via triage bot.", "APPROVE", null, installationId).block();
                case "request-changes" ->
                        gitHubApiClient.submitReview(payload.owner(), payload.repo(), payload.prNumber(),
                                "Changes requested via triage bot.", "REQUEST_CHANGES", null, installationId).block();
                case "close" ->
                        gitHubApiClient.closePullRequest(payload.owner(), payload.repo(), payload.prNumber(),
                                installationId).block();
                default -> log.warn("Unknown action in token: {}", payload.action());
            }
            analysis.setActionTaken(true);
            analysis.setStatus("ACTIONED");
            prAnalysisRepository.save(analysis);
            return ResponseEntity.ok(resultPage("Action completed: " + payload.action(), true));
        } catch (Exception e) {
            log.error("Failed to execute action {} for {}/{}/PR#{}", payload.action(),
                    payload.owner(), payload.repo(), payload.prNumber(), e);
            return ResponseEntity.status(502).body(resultPage("Action failed", false));
        }
    }

    private long parseInstallationId(String id) {
        if (id == null) return 0L;
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String resultPage(String message, boolean success) {
        String color = success ? "#1a7f37" : "#cf222e";
        return "<!doctype html><html><head><meta charset='utf-8'><title>Triage Action</title></head>"
                + "<body style='font-family:system-ui,sans-serif;max-width:480px;margin:4rem auto;text-align:center'>"
                + "<h2 style='color:" + color + "'>" + escapeHtml(message) + "</h2>"
                + "<p><a href='/'>Back to dashboard</a></p></body></html>";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
