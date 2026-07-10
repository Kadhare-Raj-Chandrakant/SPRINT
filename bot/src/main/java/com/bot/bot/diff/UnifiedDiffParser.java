package com.bot.bot.diff;

import com.bot.bot.domain.ChangeChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class UnifiedDiffParser {
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@\\s+-\\d+(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@");
    private static final Pattern DIFF_GIT = Pattern.compile("diff --git a/(.+) b/(.+)");
    private static final Pattern FILE_PREFIX = Pattern.compile("^---\\s+([ab]/)?(.*)$");
    private static final Pattern PLUS_PREFIX = Pattern.compile("^\\+\\+\\+\\s+([ab]/)?(.*)$");

    public List<ChangeChunk> parse(String diffContent) {
        if (diffContent == null || diffContent.isEmpty()) {
            return new ArrayList<>();
        }

        List<ChangeChunk> chunks = new ArrayList<>();
        String[] lines = diffContent.split("\n");

        String currentFile = null;
        String changeType = "MODIFIED";
        int currentStartLine = 0;
        List<String> addedLines = new ArrayList<>();
        List<String> removedLines = new ArrayList<>();
        StringBuilder context = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("diff --git")) {
                // Flush previous chunk before starting new file
                if (currentFile != null && (!addedLines.isEmpty() || !removedLines.isEmpty())) {
                    chunks.add(buildChunk(currentFile, currentStartLine, addedLines, removedLines, changeType, context));
                }
                addedLines.clear();
                removedLines.clear();
                context.setLength(0);
                changeType = "MODIFIED"; // Reset per file

                Matcher m = DIFF_GIT.matcher(line);
                if (m.find()) {
                    currentFile = m.group(2);
                }
                continue;
            }

            if (line.startsWith("---")) {
                Matcher m = FILE_PREFIX.matcher(line);
                if (m.find() && "/dev/null".equals(m.group(2))) {
                    changeType = "ADDED";
                }
                continue;
            }

            if (line.startsWith("+++")) {
                Matcher m = PLUS_PREFIX.matcher(line);
                if (m.find() && "/dev/null".equals(m.group(2))) {
                    changeType = "DELETED";
                }
                continue;
            }

            if (line.startsWith("@@")) {
                // Process previous chunk if exists
                if (currentFile != null && (!addedLines.isEmpty() || !removedLines.isEmpty())) {
                    chunks.add(buildChunk(currentFile, currentStartLine, addedLines, removedLines, changeType, context));
                }

                Matcher m = HUNK_HEADER.matcher(line);
                if (m.find()) {
                    currentStartLine = Integer.parseInt(m.group(1));
                }
                addedLines.clear();
                removedLines.clear();
                context.setLength(0);
                continue;
            }

            if (currentFile != null) {
                if (line.startsWith("+")) {
                    String content = line.substring(1);
                    addedLines.add(content);
                    context.append("+").append(content).append("\n");
                } else if (line.startsWith("-")) {
                    String content = line.substring(1);
                    removedLines.add(content);
                    context.append("-").append(content).append("\n");
                } else if (line.startsWith(" ")) {
                    String content = line.substring(1);
                    context.append(" ").append(content).append("\n");
                }
            }
        }

        // Don't forget the last chunk
        if (currentFile != null && (!addedLines.isEmpty() || !removedLines.isEmpty())) {
            chunks.add(buildChunk(currentFile, currentStartLine, addedLines, removedLines, changeType, context));
        }

        return chunks;
    }

    private ChangeChunk buildChunk(String filePath, int startLine, List<String> addedLines,
                                    List<String> removedLines, String changeType, StringBuilder context) {
        return ChangeChunk.builder()
                .filePath(filePath)
                .fileType(getFileType(filePath))
                .startLine(startLine)
                .endLine(startLine + addedLines.size())
                .addedLines(new ArrayList<>(addedLines))
                .removedLines(new ArrayList<>(removedLines))
                .changeType(changeType)
                .context(context.toString())
                .build();
    }

    private String getFileType(String filePath) {
        if (filePath == null) return "unknown";
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filePath.length() - 1) {
            return filePath.substring(lastDot + 1);
        }
        return "unknown";
    }
}