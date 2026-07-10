package com.bot.bot.analysis;

import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeuristicsAnalysisEngineTest {

    @Test
    void runsAllRulesAndAggregatesFindings() {
        Rule rule = new Rule() {
            @Override
            public List<Finding> analyze(ChangeChunk chunk) {
                return List.of(Finding.builder()
                        .id("id")
                        .filePath(chunk.getFilePath())
                        .lineNumber(chunk.getStartLine())
                        .severity("HIGH")
                        .category("TEST")
                        .message("test")
                        .suggestion("suggestion")
                        .source("HEURISTIC")
                        .confidence(0.5)
                        .precedenceScore(10)
                        .build());
            }

            @Override
            public String getName() {
                return "TestRule";
            }
        };

        HeuristicsAnalysisEngine engine = new HeuristicsAnalysisEngine(List.of(rule));
        ChangeChunk chunk = ChangeChunk.builder()
                .filePath("file.java")
                .startLine(1)
                .addedLines(List.of("line1"))
                .removedLines(Collections.emptyList())
                .changeType("MODIFIED")
                .context("")
                .build();

        List<Finding> findings = engine.analyze(List.of(chunk));

        assertEquals(1, findings.size());
        assertEquals("file.java", findings.get(0).getFilePath());
    }
}

