package com.sprint.sprint.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sprint.sprint.actions.TokenException;
import com.sprint.sprint.actions.TokenService;
import com.sprint.sprint.github.GitHubApiClient;
import com.sprint.sprint.persistence.PrAnalysis;
import com.sprint.sprint.persistence.PrAnalysisRepository;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import reactor.core.publisher.Mono;

class ActionControllerTest {

    private final TokenService tokenService = Mockito.mock(TokenService.class);
    private final PrAnalysisRepository prAnalysisRepository = Mockito.mock(PrAnalysisRepository.class);
    private final GitHubApiClient gitHubApiClient = Mockito.mock(GitHubApiClient.class);

    private final ActionController controller =
            new ActionController(tokenService, prAnalysisRepository, gitHubApiClient);

    private TokenService.TokenPayload payload(String owner, String repo, int n, String action) {
        return new TokenService.TokenPayload(owner, repo, n, action,
                Instant.now().plusSeconds(3600).getEpochSecond());
    }

    private PrAnalysis seeded() {
        PrAnalysis a = new PrAnalysis();
        a.setInstallationId("123");
        a.setActionTaken(false);
        return a;
    }

    @Test
    void validCloseMarksActioned() {
        when(tokenService.verify(anyString())).thenReturn(payload("acme", "api", 7, "close"));
        PrAnalysis a = seeded();
        when(prAnalysisRepository.findLatest("acme", "api", 7)).thenReturn(Optional.of(a));
        when(gitHubApiClient.closePullRequest(anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(Mono.empty());

        ResponseEntity<String> response = controller.handleAction("t", "close");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("Action completed"));
        verify(gitHubApiClient).closePullRequest(eq("acme"), eq("api"), eq(7), anyLong());
        assertTrue(a.getActionTaken());
        assertEquals("ACTIONED", a.getStatus());
    }

    @Test
    void validApproveCallsSubmitReview() {
        when(tokenService.verify(anyString())).thenReturn(payload("acme", "api", 7, "approve"));
        PrAnalysis a = seeded();
        when(prAnalysisRepository.findLatest("acme", "api", 7)).thenReturn(Optional.of(a));
        when(gitHubApiClient.submitReview(anyString(), anyString(), anyInt(), anyString(), anyString(), any(), anyLong()))
                .thenReturn(Mono.empty());

        ResponseEntity<String> response = controller.handleAction("t", "approve");

        assertEquals(200, response.getStatusCode().value());
        verify(gitHubApiClient).submitReview(eq("acme"), eq("api"), eq(7), anyString(), eq("APPROVE"), any(), anyLong());
    }

    @Test
    void badTokenReturns400() {
        when(tokenService.verify(anyString())).thenThrow(new TokenException("bad signature"));

        ResponseEntity<String> response = controller.handleAction("x", "close");

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void usedTokenReturns410() {
        when(tokenService.verify(anyString())).thenThrow(new TokenException("token already used"));

        ResponseEntity<String> response = controller.handleAction("x", "close");

        assertEquals(410, response.getStatusCode().value());
    }

    @Test
    void actionMismatchReturns400() {
        when(tokenService.verify(anyString())).thenReturn(payload("acme", "api", 7, "approve"));

        ResponseEntity<String> response = controller.handleAction("t", "close");

        assertEquals(400, response.getStatusCode().value());
        verify(gitHubApiClient, never()).submitReview(anyString(), anyString(), anyInt(), anyString(), anyString(), any(), anyLong());
    }
}
