package com.sprint.sprint;

import com.sprint.sprint.actions.TokenService;
import com.sprint.sprint.persistence.PrAnalysis;
import com.sprint.sprint.persistence.PrAnalysisRepository;
import com.sprint.sprint.web.DashboardController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.validation.support.BindingAwareModelMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F3 (automatable core): boots the full app against a real Testcontainers
 * Postgres and verifies the dashboard renders seeded analysis from the DB.
 * The GitHub-PR / Mailtrap parts of F3 remain genuine manual QA
 * (need a GitHub App + Mailtrap account).
 */
@SpringBootTest(properties = {
        "github.app-id=12345",
        "github.client-id=test-client-id",
        "github.webhook-secret=test-secret",
        "github.private-key-path=test-key.pem",
        "app.action-secret=test-action-secret",
        "llm.enabled=false"
})
@Testcontainers
class AppIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    PrAnalysisRepository prAnalysisRepository;

    @Autowired
    TokenService tokenService;

    @Autowired
    DashboardController dashboardController;

    private final SpringTemplateEngine engine = templateEngine();

    @Test
    void contextLoads() {
    }

    @Test
    void dashboardRendersSeededPrFromPostgres() {
        PrAnalysis a = new PrAnalysis();
        a.setOwner("acme");
        a.setRepo("api");
        a.setPrNumber(99);
        a.setCommitSha("abc123");
        a.setTier("RED");
        a.setSecurityFlag(true);
        a.setSummary("Hardcoded secret in config loader");
        a.setStatus("NEW");
        a.setInstallationId("1");
        a.setAlerted(false);
        a.setActionTaken(false);
        a.setCreatedAt(java.time.Instant.now());
        prAnalysisRepository.save(a);

        BindingAwareModelMap model = new BindingAwareModelMap();
        String view = dashboardController.dashboard(model);
        String html = engine.process(view, new Context(Locale.ENGLISH, model));

        assertTrue(html.contains("acme/api #99"), html);
        assertTrue(html.contains("Hardcoded secret in config loader"), html);
        assertTrue(html.contains("🔒"), "security flag shown");
        assertTrue(html.contains("token="), "action token link rendered");
        assertFalse(html.contains("No PRs analyzed yet."), "not empty state");
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
}
