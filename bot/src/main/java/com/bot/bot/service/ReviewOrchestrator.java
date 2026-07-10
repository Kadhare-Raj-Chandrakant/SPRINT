package com.bot.bot.service;


import com.google.gson.JsonObject;
import com.bot.bot.analysis.HeuristicsAnalysisEngine;
import com.bot.bot.analysis.LLMReviewEngine;
import com.bot.bot.analysis.SummaryGenerator;
import com.bot.bot.config.AppProperties;
import com.bot.bot.diff.UnifiedDiffParser;
import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import com.bot.bot.domain.PullRequestContext;
import com.bot.bot.engine.FindingMerger;
import com.bot.bot.engine.ReviewPublisher;
import com.bot.bot.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewOrchestrator {
    private final GitHubApiClient gitHubApiClient;
    private final UnifiedDiffParser diffParser;
    private final HeuristicsAnalysisEngine heuristicsAnalysisEngine;
    private final LLMReviewEngine llmReviewEngine;
    private final FindingMerger findingMerger;
    private final ReviewPublisher reviewPublisher;
    private final SummaryGenerator summaryGenerator;
    private final AppProperties appProperties;

    /**
     * Process pull request asynchronously.
     * Fetches PR data, analyzes changes, and publishes review.
     */
    @Async("reviewTaskExecutor")
    public void processPullRequest(JsonObject webhookData) {
        log.info("Starting PR review process");

        try {
            PullRequestContext prContext = gitHubApiClient.fetchPullRequestContext(webhookData);
            processPullRequestContext(prContext).block();
        } catch (Exception e) {
            log.error("Error processing PR review", e);
        }
    }

    /**
     * Process PR context: fetch diff, analyze, and publish review.
     */
    private Mono<Void> processPullRequestContext(PullRequestContext prContext) {
        long installationId = prContext.getInstallationId();
        log.info("Processing PR {}/{}/#{} (installation {})",
                prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber(), installationId);

        return gitHubApiClient.fetchDiff(prContext.getOwner(), prContext.getRepo(),
                        prContext.getPrNumber(), installationId)
                .map(diff -> {
                    List<ChangeChunk> chunks = diffParser.parse(diff);
                    log.info("Parsed {} change chunks", chunks.size());
                    return chunks;
                })
                .flatMap(chunks -> analyzeDiff(prContext, chunks))
                .doOnError(e -> log.error("Error in PR processing", e));
    }

    /**
     * Analyze changes: run heuristics and LLM analysis, merge findings, and publish.
     */
    private Mono<Void> analyzeDiff(PullRequestContext prContext, List<ChangeChunk> chunks) {
        log.debug("Starting diff analysis");

        List<Finding> findings = new ArrayList<>();

        // 1. Run heuristics analysis (synchronous)
        if (appProperties.isHeuristicsEnabled()) {
            log.debug("Running heuristics analysis");
            List<Finding> heuristicFindings = heuristicsAnalysisEngine.analyze(chunks, prContext);
            findings.addAll(heuristicFindings);
            log.info("Heuristics found {} findings", heuristicFindings.size());
        }

        // 2. Run LLM analysis (asynchronous)
        Mono<List<Finding>> llmResult = appProperties.isLlmEnabled()
                ? llmReviewEngine.analyzeWithLLM(prContext, chunks)
                    .onErrorResume(e -> {
                        log.error("LLM analysis failed, continuing with heuristics only", e);
                        return Mono.just(new ArrayList<>());
                    })
                : Mono.just(new ArrayList<>());

        return llmResult.flatMap(llmFindings -> {
            if (llmFindings != null) {
                findings.addAll(llmFindings);
                log.info("LLM found {} findings", llmFindings.size());
            }

            // Generate structured summary
            String summary = summaryGenerator.generateSummary(prContext, findings);
            log.info("Generated structured summary for PR {}/{}/#{}:", prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber());
            log.debug(summary);

            return publishReviewWithFindings(prContext, findings);
        });
    }

    /**
     * Merge, rank, and publish review findings.
     */
    private Mono<Void> publishReviewWithFindings(PullRequestContext prContext, List<Finding> findings) {
        log.debug("Merging and ranking {} findings", findings.size());

        List<Finding> rankedFindings = findingMerger.mergeAndRank(findings);
        log.info("Final {} findings after deduplication and ranking", rankedFindings.size());

        // Generate structured summary
        String summary = summaryGenerator.generateSummary(prContext, rankedFindings);
        log.info("Generated structured summary for PR {}/{}/#{}:", prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber());
        log.debug(summary);

        boolean autoApprove = appProperties.isAutoApprove();
        boolean inlineComments = appProperties.isInlineComments();

        log.debug("Publishing review to {}/{}/PR#{} (autoApprove={}, inline={})",
                prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber(),
                autoApprove, inlineComments);

        return reviewPublisher.publishReview(
                        prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber(),
                        rankedFindings, autoApprove, inlineComments, prContext.getInstallationId(), prContext)
                .doOnSuccess(v -> {
                    log.info("Review published successfully for {}/{}/PR#{}",
                            prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber());
                    log.info("Summary:");
                    log.debug(summary);
                })
                .doOnError(e -> log.error("Error publishing review for {}/{}/PR#{}",
                        prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber(), e));
    }
}
