package com.bot.bot.analysis;

import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import com.bot.bot.domain.PullRequestContext;
import com.bot.bot.analysis.heuristics.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeuristicsAnalysisEngine {
    private final SecretsDetectionRule secretsDetectionRule;
    private final CommitMessageStyleRule commitMessageStyleRule;
    private final DiffShapeRule diffShapeRule;
    private final AccountAgeRule accountAgeRule;
    private final CommentCodeRatioRule commentCodeRatioRule;
    private final BoilerplatePhraseRule boilerplatePhraseRule;

    public List<Finding> analyze(List<ChangeChunk> chunks, PullRequestContext prContext) {
        List<Finding> findings = new ArrayList<>();

        // Run all rules in parallel
        ExecutorService executor = Executors.newFixedThreadPool(6);

        try {
            List<CompletableFuture<List<Finding>>> futures = new ArrayList<>();

            // Add all heuristic rules
            futures.add(CompletableFuture.supplyAsync(() -> secretsDetectionRule.analyze(chunks), executor));
            futures.add(CompletableFuture.supplyAsync(() -> commitMessageStyleRule.analyze(chunks), executor));
            futures.add(CompletableFuture.supplyAsync(() -> diffShapeRule.analyze(chunks), executor));
            futures.add(CompletableFuture.supplyAsync(() -> accountAgeRule.analyze(chunks), executor));
            futures.add(CompletableFuture.supplyAsync(() -> commentCodeRatioRule.analyze(chunks), executor));
            futures.add(CompletableFuture.supplyAsync(() -> boilerplatePhraseRule.analyze(chunks), executor));

            // Wait for all futures to complete
            List<CompletableFuture<List<Finding>>> completedFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures)
                    .join();

            // Collect results
            for (CompletableFuture<List<Finding>> future : completedFutures) {
                findings.addAll(future.join());
            }
        } finally {
            executor.shutdown();
        }

        return findings;
    }
}
