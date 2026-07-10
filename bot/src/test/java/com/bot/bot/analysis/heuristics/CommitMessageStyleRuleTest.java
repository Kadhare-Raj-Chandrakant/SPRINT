package com.bot.bot.analysis.heuristics;

import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CommitMessageStyleRuleTest {

    @InjectMocks
    private CommitMessageStyleRule commitMessageStyleRule;

    @Test
    void testAnalyzeWithGenericMessage() {
        // Setup test data
        List<ChangeChunk> chunks = new ArrayList<>();
        ChangeChunk chunk = new ChangeChunk();
        chunk.setFilePath("src/example.js");
        chunk.setContext("Fixes issue #123");
        chunks.add(chunk);

        // Analyze
        List<Finding> findings = commitMessageStyleRule.analyze(chunks);

        // Verify findings
        assertFalse(findings.isEmpty());
        assertEquals(1, findings.size());
        assertEquals("WARNING", findings.get(0).getSeverity());
        assertEquals("AI_LIKELIHOOD", findings.get(0).getCategory());
        assertTrue(findings.get(0).getMessage().contains("Generic or templated commit message style"));
    }

    @Test
    void testAnalyzeWithBoilerplatePhrases() {
        // Setup test data
        List<ChangeChunk> chunks = new ArrayList<>();
        ChangeChunk chunk = new ChangeChunk();
        chunk.setFilePath("src/example.js");
        chunk.setContext("This ensures optimal performance.");
        chunks.add(chunk);

        // Analyze
        List<Finding> findings = commitMessageStyleRule.analyze(chunks);

        // Verify findings
        assertFalse(findings.isEmpty());
        assertEquals(1, findings.size());
        assertEquals("WARNING", findings.get(0).getSeverity());
        assertEquals("AI_LIKELIHOOD", findings.get(0).getCategory());
        assertTrue(findings.get(0).getMessage().contains("Boilerplate phrases"));
    }

    @Test
    void testAnalyzeWithNoIssues() {
        // Setup test data
        List<ChangeChunk> chunks = new ArrayList<>();
        ChangeChunk chunk = new ChangeChunk();
        chunk.setFilePath("src/example.js");
        chunk.setContext("Remove deprecated API endpoint");
        chunks.add(chunk);

        // Analyze
        List<Finding> findings = commitMessageStyleRule.analyze(chunks);

        // Verify findings
        assertTrue(findings.isEmpty());
    }
}