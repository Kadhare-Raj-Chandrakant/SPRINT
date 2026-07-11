package com.sprint.sprint.analysis.heuristics;

import com.sprint.sprint.analysis.Rule;
import com.sprint.sprint.domain.ChangeChunk;
import com.sprint.sprint.domain.Finding;
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