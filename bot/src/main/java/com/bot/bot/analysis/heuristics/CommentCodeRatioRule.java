package com.bot.bot.analysis.heuristics;

import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class CommentCodeRatioRule implements Rule {

    @Override
    public List<Finding> analyze(List<ChangeChunk> chunks) {
        List<Finding> findings = new ArrayList<>();

        if (chunks.isEmpty()) {
            return findings;
        }

        int totalAddedLines = 0;
        int totalCommentLines = 0;

        for (ChangeChunk chunk : chunks) {
            totalAddedLines += chunk.getAddedLines().size();
            // Count comment lines in added code
            for (String line : chunk.getAddedLines()) {
                if (line.trim().startsWith("//") || line.trim().startsWith("*")) {
                    totalCommentLines++;
                }
            }
        }

        // Calculate comment ratio
        double commentRatio = totalAddedLines > 0 ? (double) totalCommentLines / totalAddedLines : 0;

        // Check for unusually high comment ratio
        if (commentRatio > 0.5) {
            findings.add(Finding.builder()
                    .id("comment-code-ratio-high")
                    .filePath("CODE_CHANGES")
                    .lineNumber(0)
                    .severity("WARNING")
                    .category("AI_LIKELIHOOD")
                    .message("High comment-to-code ratio detected. This is a common pattern in AI-generated PRs.")
                    .source("HEURISTIC")
                    .confidence(0.75)
                    .precedenceScore(550)
                    .build());
        }

        return findings;
    }
}