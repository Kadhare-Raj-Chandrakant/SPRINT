package com.sprint.sprint.email;

import com.sprint.sprint.actions.TokenService;
import com.sprint.sprint.persistence.PrAnalysis;

import java.util.List;

/**
 * Renders HTML email bodies for the daily digest and the urgent-action alert.
 * Action links (approve/reject) are included only when {@link TokenService}
 * is configured; otherwise the body is link-free.
 */
public class EmailTemplate {
    private final TokenService tokenService;

    private static final String DARK    = "#1e293b";
    private static final String ACCENT  = "#e94560";
    private static final String BG      = "#f1f5f9";
    private static final String CARD    = "#ffffff";
    private static final String TEXT    = "#1e293b";
    private static final String BODY    = "#475569";
    private static final String MUTED   = "#94a3b8";
    private static final String BORDER  = "#e2e8f0";

    private static final String RED_C    = "#dc2626";
    private static final String RED_BG   = "#fef2f2";
    private static final String YELLOW_C = "#d97706";
    private static final String YELLOW_BG= "#fffbeb";
    private static final String GREEN_C  = "#16a34a";
    private static final String GREEN_BG = "#f0fdf4";

    private static final String FW = "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;";

    public EmailTemplate(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public String renderDigest(List<PrAnalysis> analyses) {
        return emailFrame(
            analyses.size() + " PR" + (analyses.size() == 1 ? "" : "s") + " analyzed",
            tableWrap(
                brandHeader("SPRINT Digest") +
                row(bodyCell(
                    p("", BODY, "14px", "margin:0 0 16px;") +
                        analyses.size() + " pull request(s) analyzed today:" +
                    p("", BODY, "14px", "margin:0 0 16px;") +
                    (analyses.isEmpty()
                        ? p("", MUTED, "13px", "margin:0;font-style:italic;") + "No pull requests to review."
                        : prTable(analyses))
                )) +
                brandFooter()
            )
        );
    }

    public String renderAlert(PrAnalysis a) {
        String t = tier(a);
        var tc = tierColors(t);
        boolean sec = Boolean.TRUE.equals(a.getSecurityFlag());
        String prLabel = escape(a.getOwner()) + "/" + escape(a.getRepo()) + "#" + a.getPrNumber();
        return emailFrame("Action Required",
            tableWrap(
                brandHeader("Action Required") +
                row(bodyCell(
                    twoCol(
                        vAlign(left, "", tiny("", MUTED) + "PR requiring attention" + tiny("/td", MUTED) +
                            bold(prLabel, TEXT, "16px")) +
                        vAlign(right, "", badge(t, tc.c, tc.bg, "12px") +
                            (sec ? "&nbsp;" + badge("⚠ SECURITY", RED_C, RED_BG, "11px") : "")),
                        "",
                        MUTED) +
                    (sec ? callout("⚠ Security concerns flagged", RED_C, RED_BG, "#fecaca") : "") +
                    summaryBlock(a.getSummary()) +
                    actionButtons(a)
                )) +
                brandFooter()
            )
        );
    }

    // ── structural helpers ──

    private static String tableWrap(String inner) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\""
            + " style=\"background:" + CARD + ";border-radius:8px;overflow:hidden;\">"
            + inner
            + "</table>";
    }

    private static String brandHeader(String subtitle) {
        return row(
            "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\""
            + " style=\"background:" + DARK + ";\">"
            + row(
                twoCol(
                    vAlign(left, "",
                        "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>"
                        + "<td style=\"width:26px;height:26px;background:" + ACCENT + ";border-radius:5px;text-align:center;"
                        + "vertical-align:middle;font-size:12px;font-weight:700;color:#fff;" + FW + "\">S</td>"
                        + "<td style=\"padding-left:10px;font-size:18px;font-weight:700;color:#fff;\" " + FW + ">SPRINT</td>"
                        + "</tr></table>") +
                    vAlign(right, "", tiny(subtitle, MUTED)),
                    "20px 28px 14px",
                    MUTED) +
                "<tr><td style=\"height:3px;background:" + ACCENT + ";font-size:0;line-height:0;\">&nbsp;</td></tr>"
            )
            + "</table>"
        );
    }

    private static String brandFooter() {
        return row(
            "<p style=\"margin:0;font-size:11px;color:" + MUTED + ";text-align:center;" + FW + "\">"
            + "Sent by <strong style=\"color:" + BODY + "\">SPRINT</strong> &mdash; Smart Pull Request Intelligence</p>"
        );
    }

    private String prTable(List<PrAnalysis> analyses) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\"")
          .append(" style=\"border-collapse:separate;border-spacing:0;border:1px solid ").append(BORDER)
          .append(";border-radius:6px;overflow:hidden;\">")
          .append("<thead><tr>");
        for (var h : new String[]{"PR", "Tier", "Summary", ""}) {
            String align = "".equals(h) ? "right" : "left";
            sb.append("<th style=\"padding:8px 12px;background:").append(BG)
              .append(";font-size:11px;font-weight:600;color:").append(MUTED)
              .append(";text-transform:uppercase;letter-spacing:.05em;text-align:").append(align)
              .append(";border-bottom:1px solid ").append(BORDER).append(";").append(FW).append("\">")
              .append(h).append("</th>");
        }
        sb.append("</tr></thead><tbody>");
        for (PrAnalysis a : analyses) {
            String t = tier(a);
            var tc = tierColors(t);
            boolean sec = Boolean.TRUE.equals(a.getSecurityFlag());
            String prId = escape(a.getOwner()) + "/" + escape(a.getRepo()) + "#" + a.getPrNumber();
            sb.append("<tr>")
              .append(cell(prId, TEXT, "13px", "font-weight:500;"))
              .append(cell(badge(t, tc.c, tc.bg, "11px") + (sec ? badge("⚠", RED_C, RED_BG, "10px") : ""), "", "13px", ""))
              .append(cell(escape(truncate(firstLine(a.getSummary()), 100)), BODY, "12px", ""))
              .append(cellRight(styleActionLinks(a)))
              .append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private static String summaryBlock(String summary) {
        String text = summary != null ? summary : "No summary provided.";
        return box(escape(text), BG, BORDER, "12px 14px");
    }

    private static String callout(String msg, String color, String bg, String borderColor) {
        return box(msg, bg, borderColor, "10px 14px");
    }

    // ── inline-box helper ──

    private static String box(String content, String bg, String borderColor, String pad) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\""
            + " style=\"margin:16px 0 0;background:" + bg + ";border:1px solid " + borderColor + ";border-radius:6px;\">"
            + row(contentCell(content, pad))
            + "</table>";
    }

    // ── action buttons ──

    private String actionButtons(PrAnalysis a) {
        if (tokenService == null) return "";
        String approve = tokenService.buildActionUrl(a.getOwner(), a.getRepo(), a.getPrNumber(), "approve");
        String reject  = tokenService.buildActionUrl(a.getOwner(), a.getRepo(), a.getPrNumber(), "reject");
        if (approve.isEmpty() && reject.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\"")
          .append(" style=\"margin-top:16px;\"><tr><td align=\"center\" style=\"padding:0;\">");
        if (!approve.isEmpty()) {
            sb.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
              .append(" style=\"display:inline-block;\"><tr><td style=\"border-radius:6px;background:").append(GREEN_C).append(";")
              .append("text-align:center;padding:0;\">")
              .append("<a href=\"").append(approve).append("\" style=\"display:inline-block;padding:9px 20px;font-size:13px;")
              .append("font-weight:600;text-decoration:none;color:#fff;").append(FW).append("\">Approve</a>")
              .append("</td></tr></table>");
        }
        if (!reject.isEmpty()) {
            sb.append("&nbsp;&nbsp;")
              .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
              .append(" style=\"display:inline-block;\"><tr><td style=\"border-radius:6px;background:").append(RED_C).append(";")
              .append("text-align:center;padding:0;\">")
              .append("<a href=\"").append(reject).append("\" style=\"display:inline-block;padding:9px 20px;font-size:13px;")
               .append("font-weight:600;text-decoration:none;color:#fff;").append(FW).append("\">Reject</a>")
              .append("</td></tr></table>");
        }
        sb.append("</td></tr></table>");
        return sb.toString();
    }

    private String styleActionLinks(PrAnalysis a) {
        // inline action links in the digest table (compact icon-buttons)
        if (tokenService == null) return "";
        String approve = tokenService.buildActionUrl(a.getOwner(), a.getRepo(), a.getPrNumber(), "approve");
        String reject  = tokenService.buildActionUrl(a.getOwner(), a.getRepo(), a.getPrNumber(), "reject");
        if (approve.isEmpty() && reject.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        if (!approve.isEmpty()) {
            sb.append("<a href=\"").append(approve).append("\" style=\"display:inline-block;padding:4px 10px;font-size:11px;")
              .append("font-weight:600;text-decoration:none;border-radius:4px;color:#fff;background:").append(GREEN_C).append(";")
              .append(FW).append("\">Approve</a>");
        }
        if (!reject.isEmpty()) {
            sb.append("&nbsp;<a href=\"").append(reject).append("\" style=\"display:inline-block;padding:4px 10px;font-size:11px;")
              .append("font-weight:600;text-decoration:none;border-radius:4px;color:#fff;background:").append(RED_C).append(";")
              .append(FW).append("\">Reject</a>");
        }
        return sb.toString();
    }

    // ── small-cell builders ──

    private static String cell(String content, String color, String fontSize, String extra) {
        String c = color.isEmpty() ? "" : "color:" + color + ";";
        return "<td style=\"padding:8px 12px;font-size:" + fontSize + ";" + c + extra
            + "border-bottom:1px solid " + BORDER + ";" + FW + "\">" + content + "</td>";
    }

    private static String cellRight(String content) {
        return "<td style=\"padding:8px 12px;text-align:right;white-space:nowrap;"
            + "border-bottom:1px solid " + BORDER + ";" + FW + "\">" + content + "</td>";
    }

    // ── primitive inline builders ──

    private static String emailFrame(String title, String body) {
        return "<!DOCTYPE html>\n"
            + "<html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">"
            + "</head>\n"
            + "<body style=\"margin:0;padding:0;background:" + BG + ";" + FW + "\">\n"
            + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n"
            + "<tr><td align=\"center\" style=\"padding:32px 16px;\">\n"
            + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"560\""
            + " style=\"max-width:560px;width:100%;\">\n"
            + body + "\n"
            + "</table>\n"
            + "</td></tr></table>\n"
            + "</body></html>";
    }

    private static String row(String inner) {
        return "<tr><td style=\"padding:0;\">" + inner + "</td></tr>";
    }

    private static String bodyCell(String inner) {
        return "<td style=\"padding:20px 28px 24px;\">" + inner + "</td>";
    }

    private static String contentCell(String inner, String pad) {
        return "<td style=\"padding:" + pad + ";font-size:13px;font-weight:600;color:" + TEXT + ";" + FW + "\">"
            + inner + "</td>";
    }

    private static String bold(String text, String color, String size) {
        return "<p style=\"margin:0;font-size:" + size + ";font-weight:600;color:" + color + ";" + FW + "\">"
            + text + "</p>";
    }

    private static String tiny(String text, String color) {
        return "<p style=\"margin:0 0 2px;font-size:11px;color:" + color + ";" + FW + "\">"
            + text + "</p>";
    }

    private static String p(String text, String color, String size, String extra) {
        return "<p style=\"margin:0;font-size:" + size + ";color:" + color + ";" + FW + extra + "\">"
            + text + "</p>";
    }

    private static String badge(String label, String color, String bg, String size) {
        return "<span style=\"display:inline-block;padding:1px 8px;font-size:" + size + ";font-weight:600;"
            + "color:" + color + ";background:" + bg + ";border-radius:8px;" + FW + "\">"
            + label + "</span>";
    }

    private static String twoCol(String left, String right, String pad) {
        return "<tr><td style=\"padding:" + pad + ";\">"
            + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\"><tr>"
            + left + right
            + "</tr></table>"
            + "</td></tr>";
    }

    private static String vAlign(String align, String style, String content) {
        return "<td align=\"" + align + "\" style=\"vertical-align:middle;" + style + "\">" + content + "</td>";
    }

    private static String left  = "left";
    private static String right = "right";

    private record TierColors(String c, String bg) {}
    private static TierColors tierColors(String t) {
        return switch (t.toUpperCase()) {
            case "RED"    -> new TierColors(RED_C, RED_BG);
            case "YELLOW" -> new TierColors(YELLOW_C, YELLOW_BG);
            case "GREEN"  -> new TierColors(GREEN_C, GREEN_BG);
            default       -> new TierColors(MUTED, BG);
        };
    }

    private static String tier(PrAnalysis a) {
        return a.getTier() != null ? a.getTier() : "UNKNOWN";
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
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
