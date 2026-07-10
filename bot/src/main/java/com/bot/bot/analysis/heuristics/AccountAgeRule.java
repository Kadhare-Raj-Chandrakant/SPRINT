package com.bot.bot.analysis.heuristics;

import com.bot.bot.analysis.Rule;
import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AccountAgeRule implements Rule {

    @Override
    public List<Finding> analyze(List<ChangeChunk> chunks) {
        List<Finding> findings = new ArrayList<>();
        // Placeholder: account age check would use GitHub API to verify author tenure
        log.warn("AccountAgeRule skipped — PullRequestContext access requires separate wiring");
        return findings;
    }

    @Override
    public String getName() {
        return "AccountAgeRule";
    }
}