package com.sprint.sprint.analysis.heuristics;

import com.sprint.sprint.domain.ChangeChunk;
import com.sprint.sprint.domain.Finding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DiffShapeRuleTest {

    @InjectMocks
    private DiffShapeRule diffShapeRule;

    @Test
    void testAnalyzeWithSweepingChanges() {
        // Setup test data
        List<ChangeChunk> chunks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ChangeChunk chunk = new ChangeChunk();
            chunk.setFilePath("src/file" + i + ".js");
            chunk.setAddedLines(Collections.singletonList("const example = 'test';"));
            chunk.setRemovedLines(Collections.emptyList());
            chunks.add(chunk);
        }

        // Analyze
        List<Finding> findings = diffShapeRule.analyze(chunks);

        // Verify findings
        assertFalse(findings.isEmpty());
        assertEquals(1, findings.size());
        assertEquals("WARNING", findings.get(0).getSeverity());
        assertEquals("AI_LIKELIHOOD", findings.get(0).getCategory());
        assertTrue(findings.get(0).getMessage().contains("Sweeping changes"));
    }

    @Test
    void testAnalyzeWithHighVolumeChanges() {
        // Setup test data
        List<ChangeChunk> chunks = new ArrayList<>();
        ChangeChunk chunk = new ChangeChunk();
        chunk.setFilePath("src/example.js");
        List<String> addedLines = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            addedLines.add("const line = '" + i + "';");
        }
        chunk.setAddedLines(addedLines);
        chunk.setRemovedLines(Collections.emptyList());
        chunks.add(chunk);

        // Analyze
        List<Finding> findings = diffShapeRule.analyze(chunks);

        // Verify findings
        assertFalse(findings.isEmpty());
        assertEquals(1, findings.size());
        assertEquals("WARNING", findings.get(0).getSeverity());
        assertEquals("AI_LIKELIHOOD", findings.get(0).getCategory());
        assertTrue(findings.get(0).getMessage().contains("High volume of changes"));
    }

    @Test
    void testAnalyzeWithNoIssues() {
        // Setup test data
        List<ChangeChunk> chunks = new ArrayList<>();
        ChangeChunk chunk = new ChangeChunk();
        chunk.setFilePath("src/example.js");
        chunk.setAddedLines(Collections.singletonList("const example = 'test';"));
        chunk.setRemovedLines(Collections.emptyList());
        chunks.add(chunk);

        // Analyze
        List<Finding> findings = diffShapeRule.analyze(chunks);

        // Verify findings
        assertTrue(findings.isEmpty());
    }
}