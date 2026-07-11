package com.sprint.sprint.analysis;

import com.sprint.sprint.domain.Finding;
import com.sprint.sprint.domain.PullRequestContext;
import com.sprint.sprint.domain.TriageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryGenerator {

    /**
     * Generates a structured summary for a PR based on findings and context.
     *
     * @param prContext The PR context containing metadata
     * @param findings List of findings from analysis engines
     * @return Structured summary as a formatted string
     */
    public String generateSummary(PullRequestContext prContext, List<Finding> findings) {
        // Extract AI-likelihood from findings
        String aiLikelihood = extractAILikelihood(findings);

        // Extract risk level from findings
        String riskLevel = extractRiskLevel(findings);

        // Extract quality signal from findings
        String qualitySignal = extractQualitySignal(findings);

        // Use computeTier for recommendation
        TriageResult triage = computeTier(findings, prContext);
        String recommendation = tierToRecommendation(triage);

        // Build the summary
        return String.format(
                """
Title: %s
Purpose: %s
Scope: %s
Risk: %s
AI-likelihood: %s - %s
Quality signal: %s
Recommendation: %s
""",
                prContext.getTitle(),
                extractPurpose(findings, prContext),
                extractScope(findings, prContext),
                riskLevel,
                aiLikelihood,
                extractAILikelihoodReason(findings),
                qualitySignal,
                recommendation
        );
    }

    /**
     * Extracts the AI-likelihood classification from findings.
     *
     * @param findings List of findings
     * @return AI-likelihood classification (LOW, MEDIUM, HIGH)
     */
    private String extractAILikelihood(List<Finding> findings) {
        return findings.stream()
                .filter(f -> "AI_LIKELIHOOD".equals(f.getCategory()))
                .findFirst()
                .map(Finding::getMessage)
                .map(message -> {
                    if (message.contains("LOW") || message.contains("HUMAN-WRITTEN")) {
                        return "LOW";
                    } else if (message.contains("MEDIUM") || message.contains("POSSIBLE AI")) {
                        return "MEDIUM";
                    } else if (message.contains("HIGH") || message.contains("AI-GENERATED")) {
                        return "HIGH";
                    }
                    return "UNKNOWN";
                }).orElse("UNKNOWN");
    }

    /**
     * Extracts the reasoning for AI-likelihood classification.
     *
     * @param findings List of findings
     * @return Reasoning for AI-likelihood classification
     */
    private String extractAILikelihoodReason(List<Finding> findings) {
        return findings.stream()
                .filter(f -> "AI_LIKELIHOOD".equals(f.getCategory()))
                .findFirst()
                .map(Finding::getMessage)
                .map(message -> {
                    // Extract reasoning after the classification
                    String[] parts = message.split("-", 2);
                    if (parts.length > 1) {
                        return parts[1].trim();
                    }
                    return "No reasoning provided.";
                }).orElse("No AI-likelihood assessment found.");
    }

    /**
     * Extracts the risk level from findings.
     *
     * @param findings List of findings
     * @return Risk level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String extractRiskLevel(List<Finding> findings) {
        return findings.stream()
                .filter(f -> "CRITICAL".equals(f.getSeverity()))
                .findFirst()
                .map(f -> "CRITICAL")
                .orElseGet(() -> {
                    return findings.stream()
                            .filter(f -> "HIGH".equals(f.getSeverity()))
                            .findFirst()
                            .map(f -> "HIGH")
                            .orElseGet(() -> {
                                return findings.stream()
                                        .filter(f -> "MEDIUM".equals(f.getSeverity()))
                                        .findFirst()
                                        .map(f -> "MEDIUM")
                                        .orElse("LOW");
                            });
                });
    }

    /**
     * Extracts the quality signal from findings.
     *
     * @param findings List of findings
     * @return Quality signal (e.g., "Includes tests", "Follows repo conventions")
     */
    private String extractQualitySignal(List<Finding> findings) {
        // Check for positive observations
        List<String> positiveObservations = findings.stream()
                .filter(f -> "POSITIVE_OBSERVATION".equals(f.getCategory()))
                .map(Finding::getMessage)
                .collect(Collectors.toList());

        if (!positiveObservations.isEmpty()) {
            return "Includes tests, follows repo conventions, and other positive observations.";
        }

        // Default quality signal
        return "No specific quality signal found.";
    }

    /**
     * Extracts the purpose of the PR from context or findings.
     *
     * @param findings List of findings
     * @param prContext The PR context
     * @return Purpose of the PR
     */
    private String extractPurpose(List<Finding> findings, PullRequestContext prContext) {
        if (prContext.getDescription() != null && !prContext.getDescription().isEmpty()) {
            return prContext.getDescription();
        }

        // If no description, try to infer from findings
        if (!findings.isEmpty()) {
            return "Change inferred from code analysis.";
        }

        return "No description available.";
    }

    /**
     * Extracts the scope of the PR.
     *
     * @param findings List of findings
     * @param prContext The PR context
     * @return Scope of the PR
     */
    private String extractScope(List<Finding> findings, PullRequestContext prContext) {
        // Count unique files changed
        int uniqueFiles = prContext.getFilesChanged().size();

        // Determine scope size
        String scopeSize = "small";
        if (uniqueFiles > 5) {
            scopeSize = "medium";
        }
        if (uniqueFiles > 10) {
            scopeSize = "large";
        }

        // Get list of files
        String filesList = String.join(", ", prContext.getFilesChanged());

        return String.format("%s files: %s", scopeSize, filesList);
    }

    /**
     * Determines the recommendation based on AI-likelihood and risk.
     *
     * @param aiLikelihood AI-likelihood classification
     * @param riskLevel Risk level
     * @return Recommendation
     */
    private String determineRecommendation(String aiLikelihood, String riskLevel) {
        if ("LOW".equals(aiLikelihood) && "LOW".equals(riskLevel)) {
            return "Merge-worthy.";
        } else if ("LOW".equals(aiLikelihood) && "MEDIUM".equals(riskLevel)) {
            return "Merge-worthy with manual review.";
        } else if ("MEDIUM".equals(aiLikelihood) || "HIGH".equals(aiLikelihood)) {
            return "Needs human review.";
        } else if ("CRITICAL".equals(riskLevel)) {
            return "Likely low-effort, consider closing.";
        } else {
            return "Needs manual inspection.";
        }
    }

    /**
     * Computes the triage tier for a PR based on SRS §5 criteria.
     *
     * @param findings  list of analysis findings (nullable)
     * @param ctx       pull request context with filesChanged and metadata
     * @return TriageResult with tier, securityFlag, and suggestedAction
     */
    public TriageResult computeTier(List<Finding> findings, PullRequestContext ctx) {
        if (findings == null) findings = List.of();
        List<String> filesChanged = ctx.getFilesChanged();
        if (filesChanged == null) filesChanged = List.of();

        // Signal extraction
        String aiLikelihood = extractAILikelihood(findings);

        boolean hasSecurityFinding = findings.stream()
                .anyMatch(f -> "SECURITY".equals(f.getCategory()));

        boolean hasTemplatedSignal = findings.stream()
                .anyMatch(f -> "AI_LIKELIHOOD".equals(f.getCategory())
                        && f.getMessage() != null
                        && f.getMessage().toLowerCase().contains("boilerplate"));

        boolean sweepingUnrelated = filesChanged.size() > 10;

        boolean noClearIntent = ctx.getDescription() == null || ctx.getDescription().isBlank();

        boolean hasTests = findings.stream()
                .anyMatch(f -> "POSITIVE_OBSERVATION".equals(f.getCategory())
                        && f.getMessage() != null
                        && f.getMessage().toLowerCase().contains("test"))
                || filesChanged.stream().anyMatch(f -> {
                    String lower = f.toLowerCase();
                    return lower.contains("test") || lower.contains("spec");
                });

        boolean isCoherent = !noClearIntent && !sweepingUnrelated;

        // RED: templated + sweeping-unrelated changes + no clear intent
        if (hasTemplatedSignal && sweepingUnrelated && noClearIntent) {
            return new TriageResult(TriageResult.Tier.RED, hasSecurityFinding,
                    TriageResult.SuggestedAction.CONSIDER_CLOSING);
        }

        // GREEN: coherent + low AI-likelihood + has tests (securityFlag is a separate axis)
        if (isCoherent && "LOW".equals(aiLikelihood) && hasTests) {
            return new TriageResult(TriageResult.Tier.GREEN, hasSecurityFinding,
                    TriageResult.SuggestedAction.REVIEW_AND_MERGE);
        }

        // YELLOW: everything else (mixed/complex/off-topic/moderate AI)
        return new TriageResult(TriageResult.Tier.YELLOW, hasSecurityFinding,
                TriageResult.SuggestedAction.MANUAL_CHECK);
    }

    private String tierToRecommendation(TriageResult triage) {
        return switch (triage.tier()) {
            case GREEN -> "Merge-worthy.";
            case YELLOW -> "Needs human review.";
            case RED -> "Requires immediate review.";
        };
    }
}