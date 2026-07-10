package com.bot.bot.web;

import com.bot.bot.actions.TokenService;
import com.bot.bot.persistence.PrAnalysis;
import com.bot.bot.persistence.PrAnalysisRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.support.BindingAwareModelMap;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardControllerTest {

    private final PrAnalysisRepository repo = mock(PrAnalysisRepository.class);
    private final TokenService tokens = mock(TokenService.class);
    private final DashboardController controller = new DashboardController(repo, tokens);

    private final SpringTemplateEngine engine = templateEngine();

    private PrAnalysis pr(String owner, String repoName, int n, String tier, boolean security, boolean actioned, String summary) {
        PrAnalysis a = new PrAnalysis();
        a.setOwner(owner);
        a.setRepo(repoName);
        a.setPrNumber(n);
        a.setTier(tier);
        a.setSecurityFlag(security);
        a.setSummary(summary);
        a.setActionTaken(actioned);
        a.setStatus(actioned ? "ACTIONED" : "NEW");
        return a;
    }

    private SpringTemplateEngine templateEngine() {
        SpringTemplateEngine e = new SpringTemplateEngine();
        ClassLoaderTemplateResolver r = new ClassLoaderTemplateResolver();
        r.setPrefix("templates/");
        r.setSuffix(".html");
        r.setTemplateMode("HTML");
        e.setTemplateResolver(r);
        return e;
    }

    private String render(String viewName, Map<String, Object> model) {
        return engine.process(viewName, new Context(Locale.ENGLISH, model));
    }

    @Test
    void getRootListsSeededPrsWithActionUrls() {
        PrAnalysis a = pr("acme", "api", 42, "RED", true, false, "Fix null deref in parser");
        when(repo.findAll(ArgumentMatchers.any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(a)));
        when(tokens.buildActionUrl("acme", "api", 42, "approve")).thenReturn("https://bot/action?token=APP&do=approve");
        when(tokens.buildActionUrl("acme", "api", 42, "request-changes")).thenReturn("https://bot/action?token=CHG&do=request-changes");
        when(tokens.buildActionUrl("acme", "api", 42, "close")).thenReturn("https://bot/action?token=CLO&do=close");

        BindingAwareModelMap model = new BindingAwareModelMap();
        String view = controller.dashboard(model);
        String html = render(view, model);

        assertTrue(html.contains("acme/api #42"), html);
        assertTrue(html.contains("token=APP") && html.contains("do=approve"), html);
        assertTrue(html.contains("token=CHG") && html.contains("do=request-changes"), html);
        assertTrue(html.contains("token=CLO") && html.contains("do=close"), html);
        assertTrue(html.contains("🔒"), "security flag emoji expected");
        assertFalse(html.contains("ACTIONED"), "not yet actioned");
        verify(repo, never()).save(ArgumentMatchers.any());
    }

    @Test
    void getRootShowsActionedState() {
        PrAnalysis a = pr("acme", "web", 7, "GREEN", false, true, "Refactor handler");
        when(repo.findAll(ArgumentMatchers.any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(a)));

        BindingAwareModelMap model = new BindingAwareModelMap();
        controller.dashboard(model);
        String html = render("dashboard", model);

        assertTrue(html.contains("ACTIONED"), html);
        assertFalse(html.contains("class=\"btn\""), "no action buttons once actioned");
    }

    @Test
    void getRootEmptyState() {
        when(repo.findAll(ArgumentMatchers.any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        BindingAwareModelMap model = new BindingAwareModelMap();
        controller.dashboard(model);
        String html = render("dashboard", model);

        assertTrue(html.contains("No PRs analyzed yet."), html);
        verify(repo, never()).save(ArgumentMatchers.any());
    }

    @Test
    void rendersUserContentEscaped() {
        PrAnalysis a = pr("o", "r", 1, "YELLOW", false, false, "<script>alert(1)</script>");
        when(repo.findAll(ArgumentMatchers.any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(a)));
        when(tokens.buildActionUrl("o", "r", 1, "approve")).thenReturn("https://bot/action?token=A&do=approve");
        when(tokens.buildActionUrl("o", "r", 1, "request-changes")).thenReturn("https://bot/action?token=B&do=request-changes");
        when(tokens.buildActionUrl("o", "r", 1, "close")).thenReturn("https://bot/action?token=C&do=close");

        BindingAwareModelMap model = new BindingAwareModelMap();
        controller.dashboard(model);
        String html = render("dashboard", model);

        assertFalse(html.contains("<script>alert(1)</script>"), "raw script must not appear");
        assertTrue(html.contains("&lt;script&gt;"), "summary must be HTML-escaped");
    }
}
