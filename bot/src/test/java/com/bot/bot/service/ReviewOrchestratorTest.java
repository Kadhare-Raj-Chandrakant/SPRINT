package com.bot.bot.service;

import com.bot.bot.analysis.HeuristicsAnalysisEngine;
import com.bot.bot.analysis.LLMReviewEngine;
import com.bot.bot.analysis.SummaryGenerator;
import com.bot.bot.config.AppProperties;
import com.bot.bot.diff.UnifiedDiffParser;
import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import com.bot.bot.domain.PullRequestContext;
import com.bot.bot.domain.TriageResult;
import com.bot.bot.email.ThresholdAlertService;
import com.bot.bot.engine.FindingMerger;
import com.bot.bot.engine.ReviewPublisher;
import com.bot.bot.github.GitHubApiClient;
import com.bot.bot.persistence.PrAnalysis;
import com.bot.bot.persistence.PrAnalysisRepository;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewOrchestratorTest {

@Test
    void processesPullRequestAndPublishesMergedFindings() {
        GitHubApiClient gitHubApiClient = Mockito.mock(GitHubApiClient.class);
        UnifiedDiffParser diffParser = Mockito.mock(UnifiedDiffParser.class);
        HeuristicsAnalysisEngine heuristicsAnalysisEngine = Mockito.mock(HeuristicsAnalysisEngine.class);
        LLMReviewEngine llmReviewEngine = Mockito.mock(LLMReviewEngine.class);
        FindingMerger findingMerger = Mockito.mock(FindingMerger.class);
        ReviewPublisher reviewPublisher = Mockito.mock(ReviewPublisher.class);
        SummaryGenerator summaryGenerator = Mockito.mock(SummaryGenerator.class);
        AppProperties appProperties = new AppProperties();
        appProperties.setHeuristicsEnabled(true);
        appProperties.setLlmEnabled(true);
        PrAnalysisRepository prAnalysisRepository = Mockito.mock(PrAnalysisRepository.class);
        Gson gson = new Gson();
        ThresholdAlertService thresholdAlertService = Mockito.mock(ThresholdAlertService.class);

 // Mock the dedup check to return false (not already processed) so test proceeds
        when(prAnalysisRepository.existsByOwnerRepoPrSha(
                anyString(), anyString(), anyInt(), anyString())).thenReturn(false);
        when(prAnalysisRepository.save(any())).thenReturn(null);

        ReviewOrchestrator orchestrator = new ReviewOrchestrator(
                gitHubApiClient, diffParser, heuristicsAnalysisEngine,
                llmReviewEngine, findingMerger, reviewPublisher,
                summaryGenerator, appProperties, prAnalysisRepository, gson,
                thresholdAlertService
        );

        PullRequestContext prContext = PullRequestContext.builder()
                .owner("owner")
                .repo("repo")
                .prNumber(1)
                .title("title")
                .description("description")
                .installationId(12345L)
                .build();

        // fetchPullRequestContext is now synchronous (no Mono)
        when(gitHubApiClient.fetchPullRequestContext(any())).thenReturn(prContext);
        when(gitHubApiClient.fetchDiff("owner", "repo", 1, 12345L)).thenReturn(Mono.just("diff"));
        ChangeChunk chunk = ChangeChunk.builder()
                .filePath("file.java")
                .startLine(1)
                .addedLines(List.of("line"))
                .removedLines(Collections.emptyList())
                .changeType("MODIFIED")
                .context("")
                .build();
        when(diffParser.parse("diff")).thenReturn(List.of(chunk));

        Finding heuristicFinding = Finding.builder()
                .id("h1")
                .filePath("file.java")
                .lineNumber(1)
                .severity("HIGH")
                .category("TEST")
                .message("heuristic")
                .suggestion("s")
                .source("HEURISTIC")
                .confidence(0.7)
                .precedenceScore(100)
                .build();
        when(heuristicsAnalysisEngine.analyze(List.of(chunk), prContext)).thenReturn(List.of(heuristicFinding));

        Finding llmFinding = Finding.builder()
                .id("l1")
                .filePath("file.java")
                .lineNumber(1)
                .severity("MEDIUM")
                .category("TEST")
                .message("llm")
                .suggestion("s")
                .source("LLM")
                .confidence(0.8)
                .precedenceScore(90)
                .build();
        when(llmReviewEngine.analyzeWithLLM(prContext, List.of(chunk))).thenReturn(Mono.just(List.of(llmFinding)));

        List<Finding> merged = List.of(heuristicFinding, llmFinding);
        when(findingMerger.mergeAndRank(any())).thenReturn(merged);

        when(summaryGenerator.generateSummary(any(), anyList())).thenReturn("summary");
        when(summaryGenerator.computeTier(anyList(), any()))
                .thenReturn(new TriageResult(TriageResult.Tier.RED, true, TriageResult.SuggestedAction.MANUAL_CHECK));

        // Match the 8-arg publishReview call (with installationId + prContext)
        when(reviewPublisher.publishReview(anyString(), anyString(), anyInt(), anyList(), anyBoolean(), anyBoolean(), anyLong(), any()))
                .thenReturn(Mono.empty());

        JsonObject webhookData = new JsonObject();
        orchestrator.processPullRequest(webhookData);

        ArgumentCaptor<List<Finding>> captor = ArgumentCaptor.forClass(List.class);
        verify(findingMerger).mergeAndRank(captor.capture());
        List<Finding> allFindings = captor.getValue();
        assertEquals(2, allFindings.size());

        verify(thresholdAlertService).maybeAlert(any(PrAnalysis.class));
    }
}
