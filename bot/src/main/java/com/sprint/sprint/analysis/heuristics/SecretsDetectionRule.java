package com.sprint.sprint.analysis.heuristics;

import com.sprint.sprint.analysis.Rule;
import com.sprint.sprint.domain.ChangeChunk;
import com.sprint.sprint.domain.Finding;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SecretsDetectionRule implements Rule {
    private static final Map<String, Pattern> SECRET_PATTERNS = Map.ofEntries(
            Map.entry("AWS_KEY", Pattern.compile("(?i)(?:aws_access_key_id|AKIA[0-9A-Z]{16})")),
            Map.entry("PRIVATE_KEY", Pattern.compile("(?i)(-----BEGIN RSA PRIVATE KEY-----|-----BEGIN PRIVATE KEY-----)")),
            Map.entry("PASSWORD", Pattern.compile("(?i)(?:password|passwd)\\s*[=:]\\s*['\\\"][^'\\\"]{4,}['\\\"]")),
            Map.entry("API_KEY", Pattern.compile("(?i)(?:api[_-]?key|apikey)\\s*[=:]\\s*['\\\"][^'\\\"]{10,}['\\\"]")),
            Map.entry("GITHUB_TOKEN", Pattern.compile("ghp_[A-Za-z0-9_]{36}")),
            Map.entry("SLACK_TOKEN", Pattern.compile("xox[baprs]-[0-9]{10,13}-[0-9]{10,13}[a-zA-Z0-9-]*"))
    );

    @Override
    public List<Finding> analyze(List<ChangeChunk> chunks) {
        List<Finding> findings = new ArrayList<>();

        for (ChangeChunk chunk : chunks) {
            for (String addedLine : chunk.getAddedLines()) {
                for (Map.Entry<String, Pattern> entry : SECRET_PATTERNS.entrySet()) {
                    if (entry.getValue().matcher(addedLine).find()) {
                        findings.add(Finding.builder()
                                .id(UUID.randomUUID().toString())
                                .filePath(chunk.getFilePath())
                                .lineNumber(chunk.getStartLine())
                                .severity("CRITICAL")
                                .category("SECURITY")
                                .message("Potential " + entry.getKey() + " detected in code")
                                .suggestion("Remove secret and rotate credentials if already exposed")
                                .source("HEURISTIC")
                                .confidence(0.95)
                                .precedenceScore(1000)
                                .build());
                    }
                }
            }
        }

        return findings;
    }

    @Override
    public String getName() {
        return "SecretsDetectionRule";
    }

}
