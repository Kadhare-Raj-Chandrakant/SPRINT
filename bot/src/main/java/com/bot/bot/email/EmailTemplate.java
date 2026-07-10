package com.bot.bot.email;

import com.bot.bot.actions.TokenService;
import com.bot.bot.persistence.PrAnalysis;

import java.util.List;

/**
 * Renders HTML email bodies for the daily digest and the urgent-action alert.
 * Action links (approve/reject) are included only when {@link TokenService}
 * is configured; otherwise the body is link-free.
 */
public class EmailTemplate {
    private final TokenService tokenService;

    public EmailTemplate(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public String renderDigest(List<PrAnalysis> analyses) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>PR Triage Digest</h2>");
        sb.append("<p>").append(analyses.size()).append(" pull request(s) analyzed:</p>");
        sb.append("<table border='1' cellpadding='6' cellspacing='0'>");
        sb.append("<tr><th>PR</th><th>Tier</th><th>Security</th><th>Summary</th><th>Actions</th></tr>");
        for (PrAnalysis a : analyses) {
            sb.append("<tr>")
                    .append("<td>").append(a.getOwner()).append("/").append(a.getRepo())
                    .append("#").append(a.getPrNumber()).append("</td>")
                    .append("<td>").append(tier(a)).append("</td>")
                    .append("<td>").append(security(a)).append("</td>")
                    .append("<td>").append(escape(truncate(firstLine(a.getSummary()), 120))).append("</td>")
                    .append("<td>").append(actionLinks(a)).append("</td>")
                    .append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    public String renderAlert(PrAnalysis a) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>Action required</h2>");
        sb.append("<p>PR <b>").append(a.getOwner()).append("/").append(a.getRepo())
                .append("#").append(a.getPrNumber()).append("</b> is <b>").append(tier(a)).append("</b>");
        if (Boolean.TRUE.equals(a.getSecurityFlag())) {
            sb.append(" (SECURITY)");
        }
        sb.append(".</p>");
        sb.append("<p>").append(escape(a.getSummary() != null ? a.getSummary() : "")).append("</p>");
        sb.append("<p>").append(actionLinks(a)).append("</p>");
        return sb.toString();
    }

    private String actionLinks(PrAnalysis a) {
        if (tokenService == null) {
            return "";
        }
        String approve = tokenService.buildActionUrl(a.getOwner(), a.getRepo(), a.getPrNumber(), "approve");
        String reject = tokenService.buildActionUrl(a.getOwner(), a.getRepo(), a.getPrNumber(), "reject");
        if (approve.isEmpty() && reject.isEmpty()) {
            return "";
        }
        return "<a href=\"" + approve + "\">Approve</a> | <a href=\"" + reject + "\">Reject</a>";
    }

    private static String tier(PrAnalysis a) {
        return a.getTier() != null ? a.getTier() : "UNKNOWN";
    }

    private static String security(PrAnalysis a) {
        return a.getSecurityFlag() != null && a.getSecurityFlag() ? "⚠ yes" : "no";
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int idx = s.indexOf('\n');
        return idx >= 0 ? s.substring(0, idx) : s;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
