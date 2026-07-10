package com.bot.bot.engine;

import com.bot.bot.analysis.SummaryGenerator;
import com.bot.bot.config.ConfigService;
import com.bot.bot.domain.Finding;
import com.bot.bot.domain.PullRequestContext;
import com.bot.bot.domain.ReviewComment;
import com.bot.bot.domain.TriageResult;
import com.bot.bot.github.GitHubApiClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewPublisherTest {

    private static final long TEST_INSTALLATION_ID = 12345L;

    private static PullRequestContext testContext() {
        return PullRequestContext.builder()
                .owner("owner").repo("repo").prNumber(1)
                .title("title").description("desc").installationId(TEST_INSTALLATION_ID)
                .build();
    }

    private static PullRequestContext testContextWithTriage(TriageResult.Tier tier, boolean securityFlag) {
        return PullRequestContext.builder()
                .owner("owner").repo("repo").prNumber(1)
                .title("title").description("desc").installationId(TEST_INSTALLATION_ID)
                .triageResult(new TriageResult(tier, securityFlag, TriageResult.SuggestedAction.MANUAL_CHECK))
                .build();
    }

    private static GitHubApiClient stubbedClient() {
        GitHubApiClient client = Mockito.mock(GitHubApiClient.class);
        when(client.submitReview(anyString(), anyString(), anyInt(), anyString(), anyString(), anyList(), anyLong()))
                .thenReturn(Mono.empty());
        when(client.addLabels(anyString(), anyString(), anyInt(), anyList(), anyLong()))
                .thenReturn(Mono.empty());
        return client;
    }

    private static ConfigService stubConfig(boolean labelsEnabled) {
        ConfigService c = Mockito.mock(ConfigService.class);
        when(c.resolve(anyLong())).thenReturn(new ConfigService.ResolvedConfig(
                "12345", "0 0 18 * * *", "RED", labelsEnabled, true, true, java.util.List.of()));
        return c;
    }

    @Test
    void publishesNoIssuesReviewWhenNoFindings() {
        GitHubApiClient client = stubbedClient();
        SummaryGenerator summaryGenerator = Mockito.mock(SummaryGenerator.class);
        when(summaryGenerator.generateSummary(any(), anyList())).thenReturn("summary");

        ReviewPublisher publisher =         new ReviewPublisher(client, summaryGenerator, stubConfig(true));
        publisher.publishReview("owner", "repo", 1, List.of(), false, true, TEST_INSTALLATION_ID, testContext()).block();

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).submitReview(
                eq("owner"), eq("repo"), eq(1),
                bodyCaptor.capture(), eq("COMMENT"), anyList(), eq(TEST_INSTALLATION_ID));
        String body = bodyCaptor.getValue();
        assertTrue(body.contains("No issues found"));
    }

    @Test
    void publishesFormattedReviewWhenFindingsPresent() {
        GitHubApiClient client = stubbedClient();
        SummaryGenerator summaryGenerator = Mockito.mock(SummaryGenerator.class);
        when(summaryGenerator.generateSummary(any(), anyList())).thenReturn("summary");

        ReviewPublisher publisher =         new ReviewPublisher(client, summaryGenerator, stubConfig(true));

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

        publisher.publishReview("owner", "repo", 1, List.of(finding), false, true, TEST_INSTALLATION_ID, testContext()).block();

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<ReviewComment>> commentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(client).submitReview(
                eq("owner"), eq("repo"), eq(1),
                bodyCaptor.capture(), eq("COMMENT"), commentsCaptor.capture(), eq(TEST_INSTALLATION_ID));

        String body = bodyCaptor.getValue();
        assertTrue(body.contains("PR Review"));
        assertTrue(body.contains("HIGH"));
        assertTrue(body.contains("file.java"));

        List<ReviewComment> comments = commentsCaptor.getValue();
        assertFalse(comments.isEmpty());
        ReviewComment inline = comments.get(0);
        assertTrue(inline.getBody().contains("HIGH"));
        assertTrue(inline.getBody().contains("Fix it"));
    }

    @Test
    void autoApprovesWhenNoFindingsAndFlagSet() {
        GitHubApiClient client = stubbedClient();
        SummaryGenerator summaryGenerator = Mockito.mock(SummaryGenerator.class);
        when(summaryGenerator.generateSummary(any(), anyList())).thenReturn("summary");

        ReviewPublisher publisher =         new ReviewPublisher(client, summaryGenerator, stubConfig(true));
        publisher.publishReview("owner", "repo", 1, List.of(), true, true, TEST_INSTALLATION_ID, testContext()).block();

        verify(client).submitReview(
                eq("owner"), eq("repo"), eq(1),
                anyString(), eq("APPROVE"), anyList(), eq(TEST_INSTALLATION_ID));
    }

    @Test
    void doesNotCreateInlineCommentForFindingWithoutLineNumber() {
        GitHubApiClient client = stubbedClient();
        SummaryGenerator summaryGenerator = Mockito.mock(SummaryGenerator.class);
        when(summaryGenerator.generateSummary(any(), anyList())).thenReturn("summary");

        ReviewPublisher publisher =         new ReviewPublisher(client, summaryGenerator, stubConfig(true));

        Finding finding = Finding.builder()
                .id("1")
                .filePath("file.java")
                .lineNumber(0)
                .severity("HIGH")
                .category("TEST")
                .message("message")
                .source("LLM")
                .confidence(0.8)
                .precedenceScore(100)
                .build();

        publisher.publishReview("owner", "repo", 1, List.of(finding), false, true, TEST_INSTALLATION_ID, testContext()).block();

        ArgumentCaptor<List<ReviewComment>> commentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(client).submitReview(
                eq("owner"), eq("repo"), eq(1),
                anyString(), eq("COMMENT"), commentsCaptor.capture(), eq(TEST_INSTALLATION_ID));
        assertTrue(commentsCaptor.getValue().isEmpty(), "No inline comments for findings without line numbers");
    }

    @Test
    void appliesTriageLabelsBasedOnTierAndSecurityFlag() {
        GitHubApiClient client = stubbedClient();
        SummaryGenerator summaryGenerator = Mockito.mock(SummaryGenerator.class);
        when(summaryGenerator.generateSummary(any(), anyList())).thenReturn("summary");

        ReviewPublisher publisher =         new ReviewPublisher(client, summaryGenerator, stubConfig(true));
        PullRequestContext ctx = testContextWithTriage(TriageResult.Tier.RED, true);

        publisher.publishReview("owner", "repo", 1, List.of(), false, true, TEST_INSTALLATION_ID, ctx).block();

        ArgumentCaptor<List<String>> labelsCaptor = ArgumentCaptor.forClass(List.class);
        verify(client).addLabels(
                eq("owner"), eq("repo"), eq(1),
                labelsCaptor.capture(), eq(TEST_INSTALLATION_ID));
        assertEquals(List.of("triage:red", "security"), labelsCaptor.getValue());
    }

    @Test
    void appliesOnlyTriageLabelWhenNoSecurityFlag() {
        GitHubApiClient client = stubbedClient();
        SummaryGenerator summaryGenerator = Mockito.mock(SummaryGenerator.class);
        when(summaryGenerator.generateSummary(any(), anyList())).thenReturn("summary");

        ReviewPublisher publisher =         new ReviewPublisher(client, summaryGenerator, stubConfig(true));
        PullRequestContext ctx = testContextWithTriage(TriageResult.Tier.GREEN, false);

        publisher.publishReview("owner", "repo", 1, List.of(), false, true, TEST_INSTALLATION_ID, ctx).block();

        ArgumentCaptor<List<String>> labelsCaptor = ArgumentCaptor.forClass(List.class);
        verify(client).addLabels(
                eq("owner"), eq("repo"), eq(1),
                labelsCaptor.capture(), eq(TEST_INSTALLATION_ID));
        assertEquals(List.of("triage:green"), labelsCaptor.getValue());
    }

    @Test
    void skipsLabelsWhenNoTriageResult() {
        GitHubApiClient client = stubbedClient();
        SummaryGenerator summaryGenerator = Mockito.mock(SummaryGenerator.class);
        when(summaryGenerator.generateSummary(any(), anyList())).thenReturn("summary");

        ReviewPublisher publisher =         new ReviewPublisher(client, summaryGenerator, stubConfig(true));

        publisher.publishReview("owner", "repo", 1, List.of(), false, true, TEST_INSTALLATION_ID, testContext()).block();

        verify(client, Mockito.never()).addLabels(anyString(), anyString(), anyInt(), anyList(), anyLong());
    }

    @Test
    void skipsLabelsWhenLabelsDisabled() {
        GitHubApiClient client = stubbedClient();
        SummaryGenerator summaryGenerator = Mockito.mock(SummaryGenerator.class);
        when(summaryGenerator.generateSummary(any(), anyList())).thenReturn("summary");

        ReviewPublisher publisher = new ReviewPublisher(client, summaryGenerator, stubConfig(false));
        PullRequestContext ctx = testContextWithTriage(TriageResult.Tier.RED, true);

        publisher.publishReview("owner", "repo", 1, List.of(), false, true, TEST_INSTALLATION_ID, ctx).block();

        verify(client, Mockito.never()).addLabels(anyString(), anyString(), anyInt(), anyList(), anyLong());
    }
}
