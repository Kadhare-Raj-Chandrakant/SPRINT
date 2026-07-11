package com.sprint.sprint.analysis;

import com.sprint.sprint.domain.ChangeChunk;
import com.sprint.sprint.domain.Finding;
import com.sprint.sprint.domain.PullRequestContext;
import com.sprint.sprint.llm.LLMClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMReviewEngine {
    private final LLMClient llmClient;

    /**
     * Analyze code chunks using LLM for contextual review.
     * Processes chunks in parallel and aggregates results.
     */
    public Mono<List<Finding>> analyzeWithLLM(PullRequestContext prContext, List<ChangeChunk> chunks) {
        log.debug("Starting LLM analysis on {} chunks", chunks.size());

        if (chunks.isEmpty()) {
            return Mono.just(new ArrayList<>());
        }

        // Convert list to Flux, process in parallel, collect results
        return Flux.fromIterable(chunks)
                .flatMap(chunk -> generateReviewForChunk(chunk, prContext))
                .flatMap(Flux::fromIterable)  // Flatten the list of findings from each chunk
                .collectList()
                .onErrorResume(e -> {
                    log.warn("Error in LLM analysis", e);
                    return Mono.just(new ArrayList<>());
                })
                .doOnSuccess(findings -> log.debug("LLM analysis completed with {} findings", findings.size()));
    }

    /**
     * Generate review for a single chunk and return list of findings.
     */
    private Mono<List<Finding>> generateReviewForChunk(ChangeChunk chunk, PullRequestContext prContext) {
        String prompt = buildPrompt(chunk, prContext);

        return llmClient.generateCodeReview(prompt)
                .map(response -> parseReviewResponse(response, chunk))
                .onErrorResume(e -> {
                    log.warn("Error generating review for chunk {}", chunk.getFilePath(), e);
                    return Mono.just(new ArrayList<>());
                });
    }

    /**
     * Build a comprehensive prompt for the LLM with context, including AI-likelihood assessment.
     */
    private String buildPrompt(ChangeChunk chunk, PullRequestContext prContext) {
        String added = chunk.getAddedLines().isEmpty()
                ? "(none)" : String.join("\n", chunk.getAddedLines());
        String removed = chunk.getRemovedLines().isEmpty()
                ? "(none)" : String.join("\n", chunk.getRemovedLines());

        return String.format(
                """
                You are an expert code reviewer. Analyze the following code change and provide specific, actionable feedback.
                
                File: %s
                Change Type: %s
                
                Added Lines (new code):
                %s
                
                Removed Lines (deleted code):
                %s
                
                Context:
                %s
                
                PR Title: %s
                PR Description: %s
                
                Provide your review in a structured format:
                1. Issues Found (if any): List each issue with severity (CRITICAL, HIGH, MEDIUM, LOW)
                2. Suggestions for Improvement
                3. Positive Observations (if any)
                4. AI-Likelihood Assessment: Classify the likelihood of AI assistance in this change as LOW, MEDIUM, or HIGH, and provide reasoning.

                Be concise and focus on substantive issues.

                For AI-Likelihood Assessment:
                - LOW: Clearly human-written, no signs of AI assistance
                - MEDIUM: Possible AI assistance, but not definitive
                - HIGH: Likely AI-generated or heavily assisted

                Include reasoning for your classification.
                """,
                chunk.getFilePath(),
                chunk.getChangeType(),
                added,
                removed,
                chunk.getContext(),
                prContext.getTitle(),
                prContext.getDescription() != null ? prContext.getDescription() : "No description"
        );
    }

    // Patterns for severity detection with word boundaries to reduce false positives
    private static final Pattern SEVERITY_CRITICAL = Pattern.compile(
            "\\b(CRITICAL|DANGER|CRITICAL ISSUE)\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEVERITY_HIGH = Pattern.compile(
            "\\b(SEVERITY:\\s*HIGH|\\[HIGH\\]|BUG|VULNERABILITY)\\b.*", Pattern.CASE_INSENSITIVE);
        private static final Pattern SEVERITY_MEDIUM = Pattern.compile(
            "\\b(SEVERITY:\\s*MEDIUM|\\[MEDIUM\\]|WARNING|MEDIUM RISK)\\b.*", Pattern.CASE_INSENSITIVE);

    // Patterns for AI-likelihood detection
    private static final Pattern AI_LIKELIHOOD_LOW = Pattern.compile(
            "\\b(AI-LIKELIHOOD:\\s*LOW|\\[LOW\\]|HUMAN-WRITTEN|LOW AI-LIKELIHOOD)\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern AI_LIKELIHOOD_MEDIUM = Pattern.compile(
            "\\b(AI-LIKELIHOOD:\\s*MEDIUM|\\[MEDIUM\\]|POSSIBLE AI|MEDIUM AI-LIKELIHOOD)\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern AI_LIKELIHOOD_HIGH = Pattern.compile(
            "\\b(AI-LIKELIHOOD:\\s*HIGH|\\[HIGH\\]|AI-GENERATED|HIGH AI-LIKELIHOOD)\\b.*", Pattern.CASE_INSENSITIVE);

    /**
     * Parse LLM response and extract findings, including AI-likelihood classification.
     */
    private List<Finding> parseReviewResponse(String response, ChangeChunk chunk) {
        List<Finding> findings = new ArrayList<>();

        if (response == null || response.isEmpty()) {
            return findings;
        }

        // Split response into lines and check for severity-labeled findings
        String[] lines = response.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Detect critical issues with word-boundary matching
            if (SEVERITY_CRITICAL.matcher(trimmed).find()) {
                findings.add(Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .filePath(chunk.getFilePath())
                        .lineNumber(chunk.getStartLine())
                        .severity("CRITICAL")
                        .category("CODE_REVIEW")
                        .message(trimmed)
                        .source("LLM")
                        .confidence(0.85)
                        .precedenceScore(700)
                        .build());
            }
            // Detect high severity issues
            else if (SEVERITY_HIGH.matcher(trimmed).find()) {
                findings.add(Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .filePath(chunk.getFilePath())
                        .lineNumber(chunk.getStartLine())
                        .severity("HIGH")
                        .category("CODE_REVIEW")
                        .message(trimmed)
                        .source("LLM")
                        .confidence(0.80)
                        .precedenceScore(650)
                        .build());
            }
            // Detect medium severity issues
            else if (SEVERITY_MEDIUM.matcher(trimmed).find()) {
                findings.add(Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .filePath(chunk.getFilePath())
                        .lineNumber(chunk.getStartLine())
                        .severity("MEDIUM")
                        .category("CODE_REVIEW")
                        .message(trimmed)
                        .source("LLM")
                        .confidence(0.75)
                        .precedenceScore(600)
                        .build());
            }
            // Detect AI-likelihood: Low
            else if (AI_LIKELIHOOD_LOW.matcher(trimmed).find()) {
                findings.add(Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .filePath(chunk.getFilePath())
                        .lineNumber(chunk.getStartLine())
                        .severity("INFO")
                        .category("AI_LIKELIHOOD")
                        .message(trimmed)
                        .source("LLM")
                        .confidence(0.85)
                        .precedenceScore(500)
                        .build());
            }
            // Detect AI-likelihood: Medium
            else if (AI_LIKELIHOOD_MEDIUM.matcher(trimmed).find()) {
                findings.add(Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .filePath(chunk.getFilePath())
                        .lineNumber(chunk.getStartLine())
                        .severity("WARNING")
                        .category("AI_LIKELIHOOD")
                        .message(trimmed)
                        .source("LLM")
                        .confidence(0.80)
                        .precedenceScore(550)
                        .build());
            }
            // Detect AI-likelihood: High
            else if (AI_LIKELIHOOD_HIGH.matcher(trimmed).find()) {
                findings.add(Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .filePath(chunk.getFilePath())
                        .lineNumber(chunk.getStartLine())
                        .severity("INFO")
                        .category("AI_LIKELIHOOD")
                        .message(trimmed)
                        .source("LLM")
                        .confidence(0.85)
                        .precedenceScore(550)
                        .build());
            }
        }

        return findings;
    }
}