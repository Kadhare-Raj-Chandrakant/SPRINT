package com.bot.bot.engine;

import com.bot.bot.domain.Finding;
import com.bot.bot.domain.PullRequestContext;
import com.bot.bot.domain.TriageResult;
import com.bot.bot.analysis.SummaryGenerator;
import com.bot.bot.domain.ReviewComment;
import com.bot.bot.config.ConfigService;
import com.bot.bot.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewPublisher {
    private final GitHubApiClient gitHubApiClient;
    private final SummaryGenerator summaryGenerator;
    private final ConfigService configService;

    public Mono<Void> publishReview(String owner, String repo, int prNumber,
                                     List<Finding> findings, boolean autoApprove,
                                     boolean inlineCommentsEnabled, long installationId, PullRequestContext prContext) {
        if (findings == null) findings = new ArrayList<>();

        // Build inline comments from findings with precise file+line info
        List<ReviewComment> inlineComments = inlineCommentsEnabled
                ? buildInlineComments(findings) : new ArrayList<>();

        // Generate structured summary
        String structuredSummary = summaryGenerator.generateSummary(prContext, findings);

        // Build the summary body
        String summary = buildReviewSummary(findings, structuredSummary);

        // Determine review event
        String event;
        if (findings.isEmpty() && autoApprove) {
            event = "APPROVE";
            log.info("Auto-approving {}/{}/PR#{} — no issues found", owner, repo, prNumber);
        } else if (findings.isEmpty()) {
            event = "COMMENT";
            log.info("No issues found for {}/{}/PR#{}", owner, repo, prNumber);
        } else {
            event = "COMMENT";
            log.info("Publishing review for {}/{}/PR#{} with {} findings ({} inline)",
                    owner, repo, prNumber, findings.size(), inlineComments.size());
        }

        boolean labelsEnabled = configService.resolve(installationId).labelsEnabled();
        return gitHubApiClient.submitReview(owner, repo, prNumber, summary, event, inlineComments, installationId)
                .then(applyTriageLabels(owner, repo, prNumber, prContext, installationId, labelsEnabled));
    }

    /**
     * Apply triage labels (triage:green/yellow/red, security) based on the computed tier.
     */
    private Mono<Void> applyTriageLabels(String owner, String repo, int prNumber,
                                           PullRequestContext prContext, long installationId,
                                           boolean labelsEnabled) {
        if (!labelsEnabled) {
            return Mono.empty();
        }
        TriageResult triage = prContext.getTriageResult();
        if (triage == null || triage.tier() == null) {
            return Mono.empty();
        }

        List<String> labels = new ArrayList<>();
        labels.add("triage:" + triage.tier().name().toLowerCase());
        if (Boolean.TRUE.equals(triage.securityFlag())) {
            labels.add("security");
        }

        log.info("Applying labels {} to {}/{}/PR#{}", labels, owner, repo, prNumber);
        return gitHubApiClient.addLabels(owner, repo, prNumber, labels, installationId);
    }

    /**
     * Build inline comments for findings that have precise line numbers (> 0).
     */
    private List<ReviewComment> buildInlineComments(List<Finding> findings) {
        List<ReviewComment> comments = new ArrayList<>();
        for (Finding f : findings) {
            if (f.getFilePath() == null || f.getLineNumber() <= 0) continue;

            StringBuilder body = new StringBuilder();
            body.append("**").append(severityLabel(f.getSeverity()))
                    .append("** ").append(f.getCategory());
            if (f.getMessage() != null) {
                body.append(": ").append(f.getMessage());
            }
            if (f.getSuggestion() != null && !f.getSuggestion().isEmpty()) {
                body.append("\n\n💡 ").append(f.getSuggestion());
            }

            ReviewComment.ReviewCommentBuilder comment = ReviewComment.builder()
                    .path(f.getFilePath())
                    .line(f.getLineNumber())
                    .side("RIGHT")
                    .body(body.toString());

            if (f.getEndLine() > f.getLineNumber()) {
                comment.startLine(f.getLineNumber());
                comment.line(f.getEndLine());
            }

            comments.add(comment.build());
        }
        return comments;
    }

    /**
     * Build the main review summary body — a structured markdown overview.
     */
    private String buildReviewSummary(List<Finding> findings, String structuredSummary) {
        StringBuilder sb = new StringBuilder();

        // Add structured summary at the beginning
        sb.append("### Structured Summary\n");
        sb.append("```\n");
        sb.append(structuredSummary);
        sb.append("```\n\n");

        sb.append("## 🤖 PR Review\n\n");

        if (findings.isEmpty()) {
            sb.append("✅ **No issues found.** The changes look good!\n\n");
            sb.append("---\n");
            sb.append("*Review generated by PR Review Bot*\n");
            return sb.toString();
        }

        // -- Summary stats --
        sb.append("### 📊 Summary\n\n");

        Map<String, Long> bySeverity = findings.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        f -> f.getSeverity() != null ? f.getSeverity() : "UNKNOWN",
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        Map<String, List<Finding>> byFile = findings.stream()
                .filter(Objects::nonNull)
                .filter(f -> f.getFilePath() != null)
                .collect(Collectors.groupingBy(Finding::getFilePath, LinkedHashMap::new, Collectors.toList()));

        // Severity breakdown bar
        sb.append("| Severity | Count |\n");
        sb.append("|----------|------:|\n");
        for (String sev : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO")) {
            long count = bySeverity.getOrDefault(sev, 0L);
            if (count > 0) {
                String emoji = switch (sev) {
                    case "CRITICAL" -> "🔴";
                    case "HIGH" -> "🟠";
                    case "MEDIUM" -> "🟡";
                    case "LOW" -> "🔵";
                    default -> "ℹ️";
                };
                sb.append("| ").append(emoji).append(" ").append(sev).append(" | ").append(count).append(" |\n");
            }
        }

        sb.append("\n**Files analyzed:** ").append(byFile.size()).append("\n\n");

        // -- Severity groups with details --
        sb.append("### 🔍 Findings\n\n");

        for (String severity : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO")) {
            List<Finding> severityFindings = findings.stream()
                    .filter(f -> f != null && severity.equals(f.getSeverity()))
                    .collect(Collectors.toList());

            if (severityFindings.isEmpty()) continue;

            String emoji = switch (severity) {
                case "CRITICAL" -> "🔴";
                case "HIGH" -> "🟠";
                case "MEDIUM" -> "🟡";
                case "LOW" -> "🔵";
                default -> "ℹ️";
            };

            sb.append("#### ").append(emoji).append(" ").append(severity)
                    .append(" (").append(severityFindings.size()).append(")\n\n");

            // Group by file within each severity
            Map<String, List<Finding>> byFileInSeverity = severityFindings.stream()
                    .collect(Collectors.groupingBy(
                            f -> f.getFilePath() != null ? f.getFilePath() : "(unknown)",
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            for (Map.Entry<String, List<Finding>> fileEntry : byFileInSeverity.entrySet()) {
                String filePath = fileEntry.getKey();
                List<Finding> fileFindings = fileEntry.getValue();

                sb.append("- **`").append(filePath).append("`** — ").append(fileFindings.size())
                        .append(" issue(s)\n");

                for (Finding finding : fileFindings) {
                    String lineStr = finding.getLineNumber() > 0 ? ":" + finding.getLineNumber() : "";
                    String cat = finding.getCategory() != null ? finding.getCategory() : "GENERAL";
                    String msg = finding.getMessage() != null ? finding.getMessage() : "";

                    sb.append("  - *").append(cat).append("*");
                    if (finding.getLineNumber() > 0) {
                        sb.append(" at `").append(filePath).append(lineStr).append("`");
                    }
                    sb.append(": ").append(msg).append("\n");

                    if (finding.getSuggestion() != null && !finding.getSuggestion().isEmpty()) {
                        sb.append("    - 💡 ").append(finding.getSuggestion()).append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        sb.append("---\n");
        String verdict = findings.stream().anyMatch(f -> "CRITICAL".equals(f.getSeverity()))
                ? "⚠️ **Action recommended:** Critical issues found that should be addressed."
                : "📝 **Review completed.** See inline comments for details.";
        sb.append(verdict).append("\n\n");
        sb.append("*Review generated by PR Review Bot*\n");

        return sb.toString();
    }

    private String severityLabel(String severity) {
        if (severity == null) return "[INFO]";
        return switch (severity) {
            case "CRITICAL" -> "[CRITICAL]";
            case "HIGH" -> "[HIGH]";
            case "MEDIUM" -> "[MEDIUM]";
            case "LOW" -> "[LOW]";
            default -> "[INFO]";
        };
    }
}
