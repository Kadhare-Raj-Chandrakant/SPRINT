package com.bot.bot.engine;

import com.bot.bot.domain.Finding;
import com.bot.bot.domain.ReviewComment;
import com.bot.bot.github.GitHubApiClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewPublisherTest {

    private static final long TEST_INSTALLATION_ID = 12345L;

    @Test
    void publishesNoIssuesReviewWhenNoFindings() {
        GitHubApiClient client = Mockito.mock(GitHubApiClient.class);
        when(client.submitReview(anyString(), anyString(), anyInt(), anyString(), anyString(), anyList(), anyLong()))
                .thenReturn(Mono.empty());

        ReviewPublisher publisher = new ReviewPublisher(client);
        publisher.publishReview("owner", "repo", 1, List.of(), false, true, TEST_INSTALLATION_ID).block();

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).submitReview(
                eq("owner"), eq("repo"), eq(1),
                bodyCaptor.capture(), eq("COMMENT"), anyList(), eq(TEST_INSTALLATION_ID));
        String body = bodyCaptor.getValue();
        assertTrue(body.contains("No issues found"));
    }

    @Test
    void publishesFormattedReviewWhenFindingsPresent() {
        GitHubApiClient client = Mockito.mock(GitHubApiClient.class);
        when(client.submitReview(anyString(), anyString(), anyInt(), anyString(), anyString(), anyList(), anyLong()))
                .thenReturn(Mono.empty());

        ReviewPublisher publisher = new ReviewPublisher(client);

        Finding finding = Finding.builder()
                .id("1")
                .filePath("file.java")
                .lineNumber(10)
                .severity("HIGH")
                .category("TEST")
                .message("Some issue here")
                .suggestion("Fix it")
                .source("HEURISTIC")
                .confidence(0.9)
                .precedenceScore(100)
                .build();

        publisher.publishReview("owner", "repo", 1, List.of(finding), false, true, TEST_INSTALLATION_ID).block();

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<ReviewComment>> commentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(client).submitReview(
                eq("owner"), eq("repo"), eq(1),
                bodyCaptor.capture(), eq("COMMENT"), commentsCaptor.capture(), eq(TEST_INSTALLATION_ID));

        // Summary body should contain overall structure
        String body = bodyCaptor.getValue();
        assertTrue(body.contains("PR Review"));
        assertTrue(body.contains("HIGH"));
        assertTrue(body.contains("file.java"));

        // Should have an inline comment for the finding with a line number
        List<ReviewComment> comments = commentsCaptor.getValue();
        assertFalse(comments.isEmpty());
        ReviewComment inline = comments.get(0);
        assertTrue(inline.getBody().contains("HIGH"));
        assertTrue(inline.getBody().contains("Fix it"));
    }

    @Test
    void autoApprovesWhenNoFindingsAndFlagSet() {
        GitHubApiClient client = Mockito.mock(GitHubApiClient.class);
        when(client.submitReview(anyString(), anyString(), anyInt(), anyString(), anyString(), anyList(), anyLong()))
                .thenReturn(Mono.empty());

        ReviewPublisher publisher = new ReviewPublisher(client);
        publisher.publishReview("owner", "repo", 1, List.of(), true, true, TEST_INSTALLATION_ID).block();

        verify(client).submitReview(
                eq("owner"), eq("repo"), eq(1),
                anyString(), eq("APPROVE"), anyList(), eq(TEST_INSTALLATION_ID));
    }

    @Test
    void doesNotCreateInlineCommentForFindingWithoutLineNumber() {
        GitHubApiClient client = Mockito.mock(GitHubApiClient.class);
        when(client.submitReview(anyString(), anyString(), anyInt(), anyString(), anyString(), anyList(), anyLong()))
                .thenReturn(Mono.empty());

        ReviewPublisher publisher = new ReviewPublisher(client);

        Finding finding = Finding.builder()
                .id("1")
                .filePath("file.java")
                .lineNumber(0) // no line number
                .severity("HIGH")
                .category("TEST")
                .message("message")
                .source("LLM")
                .confidence(0.8)
                .precedenceScore(100)
                .build();

        publisher.publishReview("owner", "repo", 1, List.of(finding), false, true, TEST_INSTALLATION_ID).block();

        ArgumentCaptor<List<ReviewComment>> commentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(client).submitReview(
                eq("owner"), eq("repo"), eq(1),
                anyString(), eq("COMMENT"), commentsCaptor.capture(), eq(TEST_INSTALLATION_ID));
        assertTrue(commentsCaptor.getValue().isEmpty(), "No inline comments for findings without line numbers");
    }
}
