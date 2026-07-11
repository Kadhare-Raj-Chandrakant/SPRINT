package com.sprint.sprint.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an inline comment on a specific line in a pull request diff.
 * Used with the GitHub PR Review API to create line-specific feedback.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewComment {
    /** File path relative to repo root (e.g. "src/main/java/Foo.java") */
    private String path;

    /** Line number in the file (1-indexed). For multi-line, this is the last line. */
    private int line;

    /** Start line for multi-line comments (optional). */
    private int startLine;

    /** Side of the diff: "LEFT" for old, "RIGHT" for new (default). */
    @Builder.Default
    private String side = "RIGHT";

    /** Comment body text. */
    private String body;
}
