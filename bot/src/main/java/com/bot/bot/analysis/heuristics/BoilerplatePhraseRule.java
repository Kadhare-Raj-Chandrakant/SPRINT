package com.bot.bot.analysis.heuristics;

import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class BoilerplatePhraseRule implements Rule {

    // Common AI boilerplate phrases
    private static final Pattern BOILERPLATE_PHRASE_PATTERN = Pattern.compile("\b(?:This ensures optimal|Here's an improved version|This fixes|This resolves|This adds|This updates|This improves|This changes|This refactors|This is a fix for|This is an improvement|This is a change|This is a refactor|This PR introduces|This PR fixes|This PR updates|This PR improves|This PR changes|This PR refactors)\\\", Pattern.CASE_INSENSITIVE);

    @Override
    public List<Finding> analyze(List<ChangeChunk> chunks) {
        List<Finding> findings = new ArrayList<>();

        if (chunks.isEmpty()) {
            return findings;
        }

        // Check for boilerplate phrases in added code
        for (ChangeChunk chunk : chunks) {
            for (String line : chunk.getAddedLines()) {
                if (BOILERPLATE_PHRASE_PATTERN.matcher(line).find()) {
                    findings.add(Finding.builder()
                            .id("boilerplate-phrase")
                            .filePath(chunk.getFilePath())
                            .lineNumber(chunk.getStartLine())
                            .severity("WARNING")
                            .category("AI_LIKELIHOOD")
                            .message("Boilerplate phrase detected in code comments. This is a common pattern in AI-generated PRs.")
                            .source("HEURISTIC")
                            .confidence(0.75)
                            .precedenceScore(550)
                            .build());
                    break; // Only report once per chunk
                }
            }
        }

        return findings;
    }
}