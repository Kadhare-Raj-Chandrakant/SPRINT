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
public class CommitMessageStyleRule implements Rule {

    @Override
    public List<Finding> analyze(List<ChangeChunk> chunks) {
        List<Finding> findings = new ArrayList<>();

        // Check if there's a commit message (PR description) available
        if (chunks.isEmpty()) {
            return findings;
        }

        // Check for generic/templated commit messages
        String prDescription = chunks.get(0).getContext(); // Assuming context contains PR description
        if (prDescription == null || prDescription.isEmpty()) {
            return findings;
        }

        // Patterns for generic/templated commit messages
        Pattern genericMessagePattern = Pattern.compile("\b(?:fix|update|add|improve|change|refactor|bump|update|patch|fixes?|implements?|adds?|changes?|fixes?|updates?|refactors?|improves?|\w+\s+issue|\w+\s+bug|\w+\s+feature)\\\", Pattern.CASE_INSENSITIVE);
        Pattern boilerplatePattern = Pattern.compile("\b(?:This ensures optimal|Here's an improved version|This fixes|This resolves|This adds|This updates|This improves|This changes|This refactors|This is a fix for|This is an improvement|This is a change|This is a refactor)\\\", Pattern.CASE_INSENSITIVE);

        // Check for generic message style
        if (genericMessagePattern.matcher(prDescription).find()) {
            findings.add(Finding.builder()
                    .id("commit-message-style")
                    .filePath("PR_DESCRIPTION")
                    .lineNumber(0)
                    .severity("WARNING")
                    .category("AI_LIKELIHOOD")
                    .message("Generic or templated commit message style detected. This is a common pattern in AI-generated PRs.")
                    .source("HEURISTIC")
                    .confidence(0.75)
                    .precedenceScore(550)
                    .build());
        }

        // Check for boilerplate phrases
        if (boilerplatePattern.matcher(prDescription).find()) {
            findings.add(Finding.builder()
                    .id("commit-message-boilerplate")
                    .filePath("PR_DESCRIPTION")
                    .lineNumber(0)
                    .severity("WARNING")
                    .category("AI_LIKELIHOOD")
                    .message("Boilerplate phrases detected in commit message. This is a common pattern in AI-generated PRs.")
                    .source("HEURISTIC")
                    .confidence(0.75)
                    .precedenceScore(550)
                    .build());
        }

        return findings;
    }
}