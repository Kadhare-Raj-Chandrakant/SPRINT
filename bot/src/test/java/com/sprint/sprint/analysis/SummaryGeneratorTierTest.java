package com.sprint.sprint.analysis;

import com.sprint.sprint.domain.Finding;
import com.sprint.sprint.domain.PullRequestContext;
import com.sprint.sprint.domain.TriageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SummaryGeneratorTierTest {

    @InjectMocks
    private SummaryGenerator summaryGenerator;

    @Test
    void green_whenCoherentLowAiHasTestsAndNoSecurity() {
        PullRequestContext ctx = new PullRequestContext();
        ctx.setTitle("Fix login validation");
        ctx.setDescription("Adds input validation to the login form to prevent XSS.");
        ctx.setFilesChanged(List.of(
                "src/LoginForm.java",
                "src/Validator.java",
                "src/test/LoginFormTest.java",
                "src/UserSession.java",
                "src/utils/Sanitizer.java"
        ));

        List<Finding> findings = new ArrayList<>();
        findings.add(Finding.builder()
                .id("ai-low")
                .category("AI_LIKELIHOOD")
                .severity("INFO")
                .message("AI-LIKELIHOOD: LOW - Clearly human-written.")
                .source("LLM")
                .confidence(0.85)
                .precedenceScore(500)
                .build());
        findings.add(Finding.builder()
                .id("test-obs")
                .category("POSITIVE_OBSERVATION")
                .severity("INFO")
                .message("PR includes test coverage for the change.")
                .source("LLM")
                .confidence(0.90)
                .precedenceScore(300)
                .build());

        TriageResult result = summaryGenerator.computeTier(findings, ctx);

        assertEquals(TriageResult.Tier.GREEN, result.tier());
        assertFalse(result.securityFlag());
        assertEquals(TriageResult.SuggestedAction.REVIEW_AND_MERGE, result.suggestedAction());
    }

    @Test
    void yellow_whenMixedOrOffTopicSignals() {
        PullRequestContext ctx = new PullRequestContext();
        ctx.setTitle("Update various files");
        ctx.setDescription("Mixed changes across the codebase.");
        ctx.setFilesChanged(List.of(
                "src/Controller.java",
                "src/Model.java"
        ));

        List<Finding> findings = new ArrayList<>();
        findings.add(Finding.builder()
                .id("ai-mid")
                .category("AI_LIKELIHOOD")
                .severity("WARNING")
                .message("AI-LIKELIHOOD: MEDIUM - Possible AI assistance.")
                .source("LLM")
                .confidence(0.80)
                .precedenceScore(550)
                .build());

        TriageResult result = summaryGenerator.computeTier(findings, ctx);

        assertEquals(TriageResult.Tier.YELLOW, result.tier());
        assertFalse(result.securityFlag());
        assertEquals(TriageResult.SuggestedAction.MANUAL_CHECK, result.suggestedAction());
    }

    @Test
    void red_whenTemplatedSweepingAndNoClearIntent() {
        PullRequestContext ctx = new PullRequestContext();
        ctx.setTitle("Update files");
        ctx.setDescription(null);

        List<String> manyFiles = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            manyFiles.add("src/module" + i + "/File.java");
        }
        ctx.setFilesChanged(manyFiles);

        List<Finding> findings = new ArrayList<>();
        findings.add(Finding.builder()
                .id("boilerplate")
                .category("AI_LIKELIHOOD")
                .severity("WARNING")
                .message("Boilerplate phrase detected in code comments.")
                .source("HEURISTIC")
                .confidence(0.75)
                .precedenceScore(550)
                .build());

        TriageResult result = summaryGenerator.computeTier(findings, ctx);

        assertEquals(TriageResult.Tier.RED, result.tier());
        assertFalse(result.securityFlag());
        assertEquals(TriageResult.SuggestedAction.CONSIDER_CLOSING, result.suggestedAction());
    }

    @Test
    void securityFlagTrue_whenSecurityFindingOnOtherwiseGreenPR() {
        PullRequestContext ctx = new PullRequestContext();
        ctx.setTitle("Fix login validation");
        ctx.setDescription("Adds input validation to the login form.");
        ctx.setFilesChanged(List.of(
                "src/LoginForm.java",
                "src/test/LoginFormTest.java"
        ));

        List<Finding> findings = new ArrayList<>();
        findings.add(Finding.builder()
                .id("ai-low")
                .category("AI_LIKELIHOOD")
                .severity("INFO")
                .message("AI-LIKELIHOOD: LOW - Clearly human-written.")
                .source("LLM")
                .confidence(0.85)
                .precedenceScore(500)
                .build());
        findings.add(Finding.builder()
                .id("test-obs")
                .category("POSITIVE_OBSERVATION")
                .severity("INFO")
                .message("PR includes test coverage.")
                .source("LLM")
                .confidence(0.90)
                .precedenceScore(300)
                .build());
        findings.add(Finding.builder()
                .id("secret-hit")
                .category("SECURITY")
                .severity("CRITICAL")
                .filePath("src/LoginForm.java")
                .lineNumber(42)
                .message("Potential API_KEY detected in code")
                .source("HEURISTIC")
                .confidence(0.95)
                .precedenceScore(1000)
                .build());

        TriageResult result = summaryGenerator.computeTier(findings, ctx);

        assertEquals(TriageResult.Tier.GREEN, result.tier());
        assertTrue(result.securityFlag());
        assertEquals(TriageResult.SuggestedAction.REVIEW_AND_MERGE, result.suggestedAction());
    }

    @Test
    void yellow_whenFindingsNull() {
        PullRequestContext ctx = new PullRequestContext();
        ctx.setTitle("Some PR");
        ctx.setDescription("Some description.");
        ctx.setFilesChanged(List.of("src/File.java"));

        TriageResult result = summaryGenerator.computeTier(null, ctx);

        assertEquals(TriageResult.Tier.YELLOW, result.tier());
        assertFalse(result.securityFlag());
        assertEquals(TriageResult.SuggestedAction.MANUAL_CHECK, result.suggestedAction());
    }

    @Test
    void yellow_whenFilesChangedNull() {
        PullRequestContext ctx = new PullRequestContext();
        ctx.setTitle("Some PR");
        ctx.setDescription("Some description.");
        ctx.setFilesChanged(null);

        List<Finding> findings = new ArrayList<>();
        findings.add(Finding.builder()
                .id("ai-low")
                .category("AI_LIKELIHOOD")
                .severity("INFO")
                .message("AI-LIKELIHOOD: LOW - Clearly human-written.")
                .source("LLM")
                .confidence(0.85)
                .precedenceScore(500)
                .build());

        TriageResult result = summaryGenerator.computeTier(findings, ctx);

        // Null filesChanged means no tests detected and coherent (no sweeping)
        // But no POSITIVE_OBSERVATION with "test" -> not GREEN -> YELLOW
        assertEquals(TriageResult.Tier.YELLOW, result.tier());
        assertFalse(result.securityFlag());
        assertEquals(TriageResult.SuggestedAction.MANUAL_CHECK, result.suggestedAction());
    }

    @Test
    void yellow_whenFindingsEmpty() {
        PullRequestContext ctx = new PullRequestContext();
        ctx.setTitle("Some PR");
        ctx.setDescription("Some description.");
        ctx.setFilesChanged(List.of("src/File.java"));

        TriageResult result = summaryGenerator.computeTier(List.of(), ctx);

        assertEquals(TriageResult.Tier.YELLOW, result.tier());
        assertFalse(result.securityFlag());
        assertEquals(TriageResult.SuggestedAction.MANUAL_CHECK, result.suggestedAction());
    }
}
