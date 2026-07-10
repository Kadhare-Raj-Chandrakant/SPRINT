package com.bot.bot.analysis.heuristics;

import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import com.bot.bot.domain.PullRequestContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AccountAgeRule implements Rule {

    @Override
    public List<Finding> analyze(List<ChangeChunk> chunks, PullRequestContext prContext) {
        List<Finding> findings = new ArrayList<>();

        if (prContext == null) {
            return findings;
        }

        // Check if the PR was opened by a new account
        // This would require GitHub API access to check account age
        // For now, we'll simulate this with a placeholder
        if (prContext.getAuthor() != null && prContext.getAuthor().contains("new")) {
            findings.add(Finding.builder()
                    .id("account-new")
                    .filePath("AUTHOR")
                    .lineNumber(0)
                    .severity("WARNING")
                    .category("AI_LIKELIHOOD")
                    .message("PR opened by a potentially new account. This is a common pattern in AI-generated PRs.")
                    .source("HEURISTIC")
                    .confidence(0.75)
                    .precedenceScore(550)
                    .build());
        }

        // Check if PR was opened shortly after account interaction
        if (prContext.getCreatedAt() != null && prContext.getUpdatedAt() != null) {
            // Simulate a check for recent account activity
            if (prContext.getCreatedAt().equals(prContext.getUpdatedAt())) {
                findings.add(Finding.builder()
                        .id("account-recent")
                        .filePath("AUTHOR")
                        .lineNumber(0)
                        .severity("WARNING")
                        .category("AI_LIKELIHOOD")
                        .message("PR opened by an account with recent activity. This is a common pattern in AI-generated PRs.")
                        .source("HEURISTIC")
                        .confidence(0.75)
                        .precedenceScore(550)
                        .build());
            }
        }

        return findings;
    }
}