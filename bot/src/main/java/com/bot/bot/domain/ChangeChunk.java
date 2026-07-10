package com.bot.bot.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeChunk {
    private String filePath;
    private String fileType;
    private int startLine;
    private int endLine;

    @Builder.Default
    private List<String> addedLines = new ArrayList<>();

    @Builder.Default
    private List<String> removedLines = new ArrayList<>();

    private String changeType; // ADDED, MODIFIED, DELETED
    private String context; // surrounding code context
}
