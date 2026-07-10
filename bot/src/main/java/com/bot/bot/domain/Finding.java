package com.bot.bot.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Finding {
    private String id;
    private String filePath;
    private int lineNumber;       // 0 means line unknown
    private int endLine;          // 0 means single-line finding
    private String severity;      // CRITICAL, HIGH, MEDIUM, LOW, INFO
    private String category;
    private String message;
    private String suggestion;
    private String source;        // HEURISTIC or LLM
    private double confidence;    // 0.0 to 1.0
    private int precedenceScore;
}
