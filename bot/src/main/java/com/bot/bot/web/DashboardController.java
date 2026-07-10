package com.bot.bot.web;

import com.bot.bot.actions.TokenService;
import com.bot.bot.persistence.PrAnalysis;
import com.bot.bot.persistence.PrAnalysisRepository;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Read-only triage dashboard (SRS §11). Lists recent PrAnalysis with one-click
 * action links built from signed tokens. GET-only; never exposes the secret or
 * performs writes. Rendered via Thymeleaf (auto-escaped).
 */
@Controller
public class DashboardController {

    private final PrAnalysisRepository prAnalysisRepository;
    private final TokenService tokenService;

    public DashboardController(PrAnalysisRepository prAnalysisRepository, TokenService tokenService) {
        this.prAnalysisRepository = prAnalysisRepository;
        this.tokenService = tokenService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        List<PrAnalysis> recent = prAnalysisRepository
                .findAll(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        List<Row> rows = recent.stream().map(this::toRow).collect(Collectors.toList());
        model.addAttribute("rows", rows);
        model.addAttribute("empty", rows.isEmpty());
        return "dashboard";
    }

    private Row toRow(PrAnalysis a) {
        return new Row(
                a.getOwner() + "/" + a.getRepo() + " #" + a.getPrNumber(),
                a.getTier(),
                tierEmoji(a),
                snippet(a.getSummary()),
                a.getStatus(),
                Boolean.TRUE.equals(a.getActionTaken()),
                tokenService.buildActionUrl(a.getOwner(), a.getRepo(), a.getPrNumber(), "approve"),
                tokenService.buildActionUrl(a.getOwner(), a.getRepo(), a.getPrNumber(), "request-changes"),
                tokenService.buildActionUrl(a.getOwner(), a.getRepo(), a.getPrNumber(), "close"));
    }

    private String tierEmoji(PrAnalysis a) {
        if (Boolean.TRUE.equals(a.getSecurityFlag())) return "🔒";
        return switch (a.getTier() == null ? "" : a.getTier().toUpperCase()) {
            case "RED" -> "🔴";
            case "YELLOW" -> "🟡";
            case "GREEN" -> "🟢";
            default -> "⚪";
        };
    }

    private String snippet(String s) {
        if (s == null) return "";
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    public record Row(String pr, String tier, String emoji, String summary,
                      String status, boolean actioned,
                      String approveUrl, String changesUrl, String closeUrl) {
    }
}
