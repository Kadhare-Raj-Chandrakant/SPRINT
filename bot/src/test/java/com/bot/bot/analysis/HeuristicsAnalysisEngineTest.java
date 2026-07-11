package com.bot.bot.analysis;

import com.bot.bot.analysis.heuristics.*;
import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import com.bot.bot.domain.PullRequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeuristicsAnalysisEngineTest {

    @Mock
    private SecretsDetectionRule secretsDetectionRule;
    @Mock
    private CommitMessageStyleRule commitMessageStyleRule;
    @Mock
    private DiffShapeRule diffShapeRule;
    @Mock
    private AccountAgeRule accountAgeRule;
    @Mock
    private CommentCodeRatioRule commentCodeRatioRule;
    @Mock
    private BoilerplatePhraseRule boilerplatePhraseRule;

    @Test
    void runsAllRulesAndAggregatesFindings() {
        ChangeChunk chunk = ChangeChunk.builder()
                .filePath("file.java")
                .startLine(1)
                .addedLines(List.of("line1"))
                .removedLines(Collections.emptyList())
                .changeType("MODIFIED")
                .context("")
                .build();
        List<ChangeChunk> chunks = List.of(chunk);

        Finding expectedFinding = Finding.builder()
                .id("id")
                .filePath("file.java")
                .lineNumber(1)
                .severity("HIGH")
                .category("TEST")
                .message("test")
                .suggestion("suggestion")
                .source("HEURISTIC")
                .confidence(0.5)
                .precedenceScore(10)
                .build();

        when(secretsDetectionRule.analyze(chunks)).thenReturn(List.of(expectedFinding));
        when(commitMessageStyleRule.analyze(chunks)).thenReturn(Collections.emptyList());
        when(diffShapeRule.analyze(chunks)).thenReturn(Collections.emptyList());
        when(accountAgeRule.analyze(chunks)).thenReturn(Collections.emptyList());
        when(commentCodeRatioRule.analyze(chunks)).thenReturn(Collections.emptyList());
        when(boilerplatePhraseRule.analyze(chunks)).thenReturn(Collections.emptyList());

        HeuristicsAnalysisEngine engine = new HeuristicsAnalysisEngine(
                secretsDetectionRule, commitMessageStyleRule, diffShapeRule,
                accountAgeRule, commentCodeRatioRule, boilerplatePhraseRule,
                Executors.newFixedThreadPool(6));

        PullRequestContext prContext = PullRequestContext.builder()
                .owner("owner").repo("repo").prNumber(1)
                .title("title").description("desc").installationId(1L)
                .build();

        List<Finding> findings = engine.analyze(chunks, prContext);

        assertEquals(1, findings.size());
        assertEquals("file.java", findings.get(0).getFilePath());
    }
}
