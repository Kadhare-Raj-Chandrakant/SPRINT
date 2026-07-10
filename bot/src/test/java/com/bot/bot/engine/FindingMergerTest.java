package com.bot.bot.engine;

import com.bot.bot.domain.Finding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindingMergerTest {

    @Test
    void mergesDuplicateFindingsByKeepingHigherConfidence() {
        Finding lowConfidence = Finding.builder()
                .id("1")
                .filePath("file.java")
                .lineNumber(10)
                .severity("HIGH")
                .category("TEST")
                .message("m1")
                .suggestion("s1")
                .source("HEURISTIC")
                .confidence(0.5)
                .precedenceScore(100)
                .build();

        Finding highConfidence = Finding.builder()
                .id("2")
                .filePath("file.java")
                .lineNumber(10)
                .severity("HIGH")
                .category("TEST")
                .message("m2")
                .suggestion("s2")
                .source("LLM")
                .confidence(0.9)
                .precedenceScore(100)
                .build();

        FindingMerger merger = new FindingMerger();
        List<Finding> merged = merger.mergeAndRank(List.of(lowConfidence, highConfidence));

        assertEquals(1, merged.size());
        assertEquals("2", merged.get(0).getId());
    }

    @Test
    void sortsByPrecedenceAndSeverity() {
        Finding lowSeverity = Finding.builder()
                .id("1")
                .filePath("file.java")
                .lineNumber(10)
                .severity("LOW")
                .category("TEST")
                .message("m1")
                .suggestion("s1")
                .source("HEURISTIC")
                .confidence(0.5)
                .precedenceScore(100)
                .build();

        Finding highSeverity = Finding.builder()
                .id("2")
                .filePath("file2.java")
                .lineNumber(5)
                .severity("CRITICAL")
                .category("TEST")
                .message("m2")
                .suggestion("s2")
                .source("HEURISTIC")
                .confidence(0.5)
                .precedenceScore(200)
                .build();

        FindingMerger merger = new FindingMerger();
        List<Finding> merged = merger.mergeAndRank(List.of(lowSeverity, highSeverity));

        assertEquals(2, merged.size());
        assertEquals("2", merged.get(0).getId());
        assertTrue(merged.get(0).getPrecedenceScore() >= merged.get(1).getPrecedenceScore());
    }
}

