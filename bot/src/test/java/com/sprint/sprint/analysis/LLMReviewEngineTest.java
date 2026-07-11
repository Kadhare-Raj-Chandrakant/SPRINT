package com.sprint.sprint.analysis;

import com.sprint.sprint.domain.ChangeChunk;
import com.sprint.sprint.domain.Finding;
import com.sprint.sprint.domain.PullRequestContext;
import com.sprint.sprint.llm.LLMClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LLMReviewEngineTest {

    @Mock
    private LLMClient llmClient;

    @InjectMocks
    private LLMReviewEngine llmReviewEngine;

    @Test
    void testParseReviewResponseWithAILikelihood() {
        // Setup test data
        String response = """
                Issues Found:
                - CRITICAL: Security vulnerability detected
                - AI-LIKELIHOOD: LOW - Clearly human-written, no signs of AI assistance.
                Suggestions for Improvement:
                - Fix the security vulnerability
                Positive Observations:
                - Code follows project conventions
                """;

        ChangeChunk chunk = new ChangeChunk();
        chunk.setFilePath("src/security.js");
        chunk.setStartLine(10);

        @SuppressWarnings("unchecked")
        List<Finding> findings = (List<Finding>) ReflectionTestUtils.invokeMethod(
                llmReviewEngine, "parseReviewResponse", response, chunk);

        // Verify findings
        assertEquals(3, findings.size());
        assertTrue(findings.stream().anyMatch(f -> "CRITICAL".equals(f.getSeverity())));
        assertTrue(findings.stream().anyMatch(f -> "INFO".equals(f.getSeverity()) && "AI_LIKELIHOOD".equals(f.getCategory())));
        assertEquals("src/security.js", findings.stream().filter(f -> "INFO".equals(f.getSeverity()) && "AI_LIKELIHOOD".equals(f.getCategory())).findFirst().orElse(null).getFilePath());
    }

    @Test
    void testParseReviewResponseWithHighAILikelihood() {
        // Setup test data
        String response = """
                Issues Found:
                - CRITICAL: Security vulnerability detected
                - AI-LIKELIHOOD: HIGH - Likely AI-generated or heavily assisted.
                Suggestions for Improvement:
                - Fix the security vulnerability
                Positive Observations:
                - Code follows project conventions
                """;

        ChangeChunk chunk = new ChangeChunk();
        chunk.setFilePath("src/security.js");
        chunk.setStartLine(10);

        @SuppressWarnings("unchecked")
        List<Finding> findings = (List<Finding>) ReflectionTestUtils.invokeMethod(
                llmReviewEngine, "parseReviewResponse", response, chunk);

        // Verify findings
        assertEquals(3, findings.size());
        assertTrue(findings.stream().anyMatch(f -> "CRITICAL".equals(f.getSeverity())));
        assertTrue(findings.stream().anyMatch(f -> "INFO".equals(f.getSeverity()) && "AI_LIKELIHOOD".equals(f.getCategory())));
        assertEquals("src/security.js", findings.stream().filter(f -> "INFO".equals(f.getSeverity()) && "AI_LIKELIHOOD".equals(f.getCategory())).findFirst().orElse(null).getFilePath());
    }

    @Test
    void testBuildPromptIncludesAILikelihood() {
        // Setup test data
        ChangeChunk chunk = new ChangeChunk();
        chunk.setFilePath("src/example.js");
        chunk.setChangeType("ADD");
        chunk.setAddedLines(Collections.singletonList("const example = 'test';"));
        chunk.setRemovedLines(Collections.emptyList());
        chunk.setContext("");

        PullRequestContext prContext = new PullRequestContext();
        prContext.setTitle("Example PR");
        prContext.setDescription("This is an example PR.");

        String prompt = (String) ReflectionTestUtils.invokeMethod(
                llmReviewEngine, "buildPrompt", chunk, prContext);

        // Verify prompt includes AI-likelihood instructions
        assertTrue(prompt.contains("AI-Likelihood Assessment:"));
        assertTrue(prompt.contains("LOW"));
        assertTrue(prompt.contains("MEDIUM"));
        assertTrue(prompt.contains("HIGH"));
    }
}