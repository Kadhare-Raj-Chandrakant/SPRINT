package com.bot.bot.webhook;

import com.bot.bot.service.ReviewOrchestrator;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

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

    @Test
    void returnsUnauthorizedWhenSignatureInvalid() {
        WebhookSignatureVerifier signatureVerifier = Mockito.mock(WebhookSignatureVerifier.class);
        ReviewOrchestrator reviewOrchestrator = Mockito.mock(ReviewOrchestrator.class);
        Gson gson = new Gson();
        GitHubWebhookController controller = new GitHubWebhookController(signatureVerifier, reviewOrchestrator, gson);

        when(signatureVerifier.verifySignature(any(), any())).thenReturn(false);

        ResponseEntity<String> response = controller.handleGitHubWebhook(
                "{\"action\":\"opened\"}",
                "invalid",
                "pull_request",
                "delivery-id"
        );

        assertEquals(401, response.getStatusCode().value());
        assertEquals("Invalid signature", response.getBody());
        verify(reviewOrchestrator, never()).processPullRequest(any());
    }

    @Test
    void ignoresNonPullRequestEvents() {
        WebhookSignatureVerifier signatureVerifier = Mockito.mock(WebhookSignatureVerifier.class);
        ReviewOrchestrator reviewOrchestrator = Mockito.mock(ReviewOrchestrator.class);
        Gson gson = new Gson();
        GitHubWebhookController controller = new GitHubWebhookController(signatureVerifier, reviewOrchestrator, gson);

        when(signatureVerifier.verifySignature(any(), any())).thenReturn(true);

        ResponseEntity<String> response = controller.handleGitHubWebhook(
                "{\"action\":\"opened\"}",
                "valid",
                "push",
                "delivery-id"
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Event ignored", response.getBody());
        verify(reviewOrchestrator, never()).processPullRequest(any());
    }

    @Test
    void processesPullRequestForSupportedActions() {
        WebhookSignatureVerifier signatureVerifier = Mockito.mock(WebhookSignatureVerifier.class);
        ReviewOrchestrator reviewOrchestrator = Mockito.mock(ReviewOrchestrator.class);
        Gson gson = new Gson();
        GitHubWebhookController controller = new GitHubWebhookController(signatureVerifier, reviewOrchestrator, gson);

        when(signatureVerifier.verifySignature(any(), any())).thenReturn(true);

        String payload = "{\"action\":\"opened\"}";

        ResponseEntity<String> response = controller.handleGitHubWebhook(
                payload,
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
    void ignoresUnsupportedPullRequestActions() {
        WebhookSignatureVerifier signatureVerifier = Mockito.mock(WebhookSignatureVerifier.class);
        ReviewOrchestrator reviewOrchestrator = Mockito.mock(ReviewOrchestrator.class);
        Gson gson = new Gson();
        GitHubWebhookController controller = new GitHubWebhookController(signatureVerifier, reviewOrchestrator, gson);

        when(signatureVerifier.verifySignature(any(), any())).thenReturn(true);

        ResponseEntity<String> response = controller.handleGitHubWebhook(
                "{\"action\":\"closed\"}",
                "valid",
                "pull_request",
                "delivery-id"
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Action ignored", response.getBody());
        verify(reviewOrchestrator, never()).processPullRequest(any());
    }
}
