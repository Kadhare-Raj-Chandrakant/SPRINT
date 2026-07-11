package com.sprint.sprint.analysis;

import com.sprint.sprint.domain.Finding;
import com.sprint.sprint.domain.PullRequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SummaryGeneratorTest {

    @InjectMocks
    private SummaryGenerator summaryGenerator;

    @Test
    void testGenerateSummaryWithAILikelihood() {
        // Setup test data
        PullRequestContext prContext = new PullRequestContext();
        prContext.setTitle("Fix login bug");
        prContext.setDescription("This fixes a bug in the login process.");
        prContext.setFilesChanged(Collections.singletonList("src/login.js"));

        // Create findings with AI-likelihood
        List<Finding> findings = new ArrayList<>();
        findings.add(Finding.builder()
                .id("ai-low")
                .filePath("PR_DESCRIPTION")
                .lineNumber(0)
                .severity("INFO")
                .category("AI_LIKELIHOOD")
                .message("AI-LIKELIHOOD: LOW - Clearly human-written, no signs of AI assistance.")
                .source("LLM")
                .confidence(0.85)
                .precedenceScore(500)
                .build());

        // Generate summary
        String summary = summaryGenerator.generateSummary(prContext, findings);

        // Verify summary contains expected information
        assertTrue(summary.contains("Title: Fix login bug"));
        assertTrue(summary.contains("Purpose: This fixes a bug in the login process."));
        assertTrue(summary.contains("AI-likelihood: LOW"));
        assertTrue(summary.contains("Clearly human-written, no signs of AI assistance."));
    }

    @Test
    void testGenerateSummaryWithHighRisk() {
        // Setup test data
        PullRequestContext prContext = new PullRequestContext();
        prContext.setTitle("Update dependencies");
        prContext.setDescription("Update all dependencies to latest versions.");
        prContext.setFilesChanged(Collections.singletonList("package.json"));

        // Create findings with high risk
        List<Finding> findings = new ArrayList<>();
        findings.add(Finding.builder()
                .id("risk-critical")
                .filePath("package.json")
                .lineNumber(1)
                .severity("CRITICAL")
                .category("CODE_REVIEW")
                .message("Critical security vulnerability detected.")
                .source("LLM")
                .confidence(0.9)
                .precedenceScore(700)
                .build());

        // Generate summary
        String summary = summaryGenerator.generateSummary(prContext, findings);

        // Verify summary contains expected information
        assertTrue(summary.contains("Title: Update dependencies"));
        assertTrue(summary.contains("Risk: CRITICAL"));
        assertTrue(summary.contains("Needs human review"));
    }

    @Test
    void testGenerateSummaryWithMediumAILikelihood() {
        // Setup test data
        PullRequestContext prContext = new PullRequestContext();
        prContext.setTitle("Add new feature");
        prContext.setDescription("This adds a new feature to the application.");
        prContext.setFilesChanged(Collections.singletonList("src/features"));

        // Create findings with medium AI-likelihood
        List<Finding> findings = new ArrayList<>();
        findings.add(Finding.builder()
                .id("ai-medium")
                .filePath("PR_DESCRIPTION")
                .lineNumber(0)
                .severity("WARNING")
                .category("AI_LIKELIHOOD")
                .message("AI-LIKELIHOOD: MEDIUM - Possible AI assistance, but not definitive.")
                .source("LLM")
                .confidence(0.8)
                .precedenceScore(550)
                .build());

        // Generate summary
        String summary = summaryGenerator.generateSummary(prContext, findings);

        // Verify summary contains expected information
        assertTrue(summary.contains("Title: Add new feature"));
        assertTrue(summary.contains("AI-likelihood: MEDIUM"));
        assertTrue(summary.contains("Possible AI assistance, but not definitive"));
        assertTrue(summary.contains("Needs human review"));
    }
}