package com.sprint.sprint.engine;

import com.sprint.sprint.domain.Finding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FindingMerger {

    public List<Finding> mergeAndRank(List<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            return new ArrayList<>();
        }

        log.debug("Merging and ranking {} findings", findings.size());

        // Deduplicate similar findings by filePath:lineNumber:category
        Map<String, Finding> merged = new LinkedHashMap<>();

        for (Finding finding : findings) {
            if (finding == null) continue;

            String key = generateKey(finding);

            Finding existing = merged.get(key);
            if (existing == null) {
                merged.put(key, finding);
            } else if (finding.getConfidence() > existing.getConfidence()) {
                // Keep the one with higher confidence
                merged.put(key, finding);
            }
        }

        // Sort by precedence and severity
        return merged.values().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt((Finding f) -> -f.getPrecedenceScore())
                        .thenComparing(f -> severityToInt(f.getSeverity()), Comparator.reverseOrder())
                        .thenComparingDouble(f -> -f.getConfidence())
                )
                .collect(Collectors.toList());
    }

    private String generateKey(Finding finding) {
        String filePath = finding.getFilePath() != null ? finding.getFilePath() : "";
        String category = finding.getCategory() != null ? finding.getCategory() : "";
        return String.format("%s:%d:%s", filePath, finding.getLineNumber(), category);
    }

    private int severityToInt(String severity) {
        if (severity == null) return -1;
        return switch (severity) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            case "INFO" -> 0;
            default -> -1;
        };
    }
}
