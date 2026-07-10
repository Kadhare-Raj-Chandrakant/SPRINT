package com.bot.bot.analysis.heuristics;

import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class DiffShapeRule implements Rule {

    @Override
    public List<Finding> analyze(List<ChangeChunk> chunks) {
        List<Finding> findings = new ArrayList<>();

        if (chunks.isEmpty()) {
            return findings;
        }

        // Track unique files changed
        Set<String> uniqueFiles = new HashSet<>();
        int totalLinesAdded = 0;
        int totalLinesRemoved = 0;

        for (ChangeChunk chunk : chunks) {
            uniqueFiles.add(chunk.getFilePath());
            totalLinesAdded += chunk.getAddedLines().size();
            totalLinesRemoved += chunk.getRemovedLines().size();
        }

        // Check for sweeping changes across many unrelated files
        if (uniqueFiles.size() > 5) {
            findings.add(Finding.builder()
                    .id("diff-sweeping")
                    .filePath("MULTIPLE_FILES")
                    .lineNumber(0)
                    .severity("WARNING")
                    .category("AI_LIKELIHOOD")
                    .message("Sweeping changes across many unrelated files detected. This is a common pattern in AI-generated PRs.")
                    .source("HEURISTIC")
                    .confidence(0.75)
                    .precedenceScore(550)
                    .build());
        }

        // Check for unusually high line count changes
        int totalLinesChanged = totalLinesAdded + totalLinesRemoved;
        if (totalLinesChanged > 100) {
            findings.add(Finding.builder()
                    .id("diff-high-volume")
                    .filePath("MULTIPLE_FILES")
                    .lineNumber(0)
                    .severity("WARNING")
                    .category("AI_LIKELIHOOD")
                    .message("High volume of changes detected. This is a common pattern in AI-generated PRs.")
                    .source("HEURISTIC")
                    .confidence(0.75)
                    .precedenceScore(550)
                    .build());
        }

        return findings;
    }
}