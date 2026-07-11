package com.sprint.sprint.webhook;

import com.sprint.sprint.service.ReviewOrchestrator;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubWebhookControllerTest {

    @Test
    void healthEndpointReturnsOk() {
        WebhookSignatureVerifier signatureVerifier = Mockito.mock(WebhookSignatureVerifier.class);
        ReviewOrchestrator reviewOrchestrator = Mockito.mock(ReviewOrchestrator.class);
        Gson gson = new Gson();
        GitHubWebhookController controller = new GitHubWebhookController(signatureVerifier, reviewOrchestrator, gson);

        ResponseEntity<String> response = controller.health();

        assertEquals(200, response.getStatusCode().value());
        assertEquals("OK", response.getBody());
    }

    private static HttpServletRequest mockRequest(String body) throws Exception {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        when(req.getInputStream()).thenReturn(
                new jakarta.servlet.ServletInputStream() {
                    private final ByteArrayInputStream bis = new ByteArrayInputStream(
                            body.getBytes(StandardCharsets.UTF_8));
                    @Override public int read() { return bis.read(); }
                    @Override public boolean isFinished() { return bis.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(jakarta.servlet.ReadListener l) {}
                });
        return req;
    }

    @Test
    void returnsUnauthorizedWhenSignatureInvalid() throws Exception {
        WebhookSignatureVerifier signatureVerifier = Mockito.mock(WebhookSignatureVerifier.class);
        ReviewOrchestrator reviewOrchestrator = Mockito.mock(ReviewOrchestrator.class);
        Gson gson = new Gson();
        GitHubWebhookController controller = new GitHubWebhookController(signatureVerifier, reviewOrchestrator, gson);

        when(signatureVerifier.verifySignature(any(), any())).thenReturn(false);

        ResponseEntity<String> response = controller.handleGitHubWebhook(
                mockRequest("{\"action\":\"opened\"}"),
                "invalid",
                "pull_request",
                "delivery-id"
        );

        assertEquals(401, response.getStatusCode().value());
        assertEquals("Invalid signature", response.getBody());
        verify(reviewOrchestrator, never()).processPullRequest(any());
    }

    @Test
    void ignoresNonPullRequestEvents() throws Exception {
        WebhookSignatureVerifier signatureVerifier = Mockito.mock(WebhookSignatureVerifier.class);
        ReviewOrchestrator reviewOrchestrator = Mockito.mock(ReviewOrchestrator.class);
        Gson gson = new Gson();
        GitHubWebhookController controller = new GitHubWebhookController(signatureVerifier, reviewOrchestrator, gson);

        when(signatureVerifier.verifySignature(any(), any())).thenReturn(true);

        ResponseEntity<String> response = controller.handleGitHubWebhook(
                mockRequest("{\"action\":\"opened\"}"),
                "valid",
                "push",
                "delivery-id"
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Event ignored", response.getBody());
        verify(reviewOrchestrator, never()).processPullRequest(any());
    }

    @Test
    void processesPullRequestForSupportedActions() throws Exception {
        WebhookSignatureVerifier signatureVerifier = Mockito.mock(WebhookSignatureVerifier.class);
        ReviewOrchestrator reviewOrchestrator = Mockito.mock(ReviewOrchestrator.class);
        Gson gson = new Gson();
        GitHubWebhookController controller = new GitHubWebhookController(signatureVerifier, reviewOrchestrator, gson);

        when(signatureVerifier.verifySignature(any(), any())).thenReturn(true);

        String payload = "{\"action\":\"opened\"}";

        ResponseEntity<String> response = controller.handleGitHubWebhook(
                mockRequest(payload),
                "valid",
                "pull_request",
                "delivery-id"
        );

        assertEquals(202, response.getStatusCode().value());
        assertEquals("Processing started", response.getBody());

        ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
        verify(reviewOrchestrator).processPullRequest(captor.capture());
        JsonObject captured = captor.getValue();
        assertEquals("opened", captured.get("action").getAsString());
    }

    @Test
    void ignoresUnsupportedPullRequestActions() throws Exception {
        WebhookSignatureVerifier signatureVerifier = Mockito.mock(WebhookSignatureVerifier.class);
        ReviewOrchestrator reviewOrchestrator = Mockito.mock(ReviewOrchestrator.class);
        Gson gson = new Gson();
        GitHubWebhookController controller = new GitHubWebhookController(signatureVerifier, reviewOrchestrator, gson);

        when(signatureVerifier.verifySignature(any(), any())).thenReturn(true);

        ResponseEntity<String> response = controller.handleGitHubWebhook(
                mockRequest("{\"action\":\"closed\"}"),
                "valid",
                "pull_request",
                "delivery-id"
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Action ignored", response.getBody());
        verify(reviewOrchestrator, never()).processPullRequest(any());
    }
}
