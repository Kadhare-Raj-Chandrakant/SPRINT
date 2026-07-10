package com.bot.bot.email;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bot.bot.actions.TokenService;
import com.bot.bot.config.AppProperties;
import com.bot.bot.persistence.Meta;
import com.bot.bot.persistence.MetaRepository;
import com.bot.bot.persistence.PrAnalysis;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

class EmailTemplateTest {

    private TokenService tokenService(boolean configured) {
        AppProperties props = new AppProperties();
        if (configured) {
            props.setActionSecret("secret");
            props.setBaseUrl("https://bot.example.com");
        }
        MetaRepository repo = Mockito.mock(MetaRepository.class);
        Mockito.when(repo.findById(Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(repo.save(Mockito.any(Meta.class))).thenAnswer(inv -> inv.getArgument(0));
        return new TokenService(props, repo);
    }

    private PrAnalysis analysis(String owner, String repo, int n, String tier, boolean security) {
        PrAnalysis a = new PrAnalysis();
        a.setOwner(owner);
        a.setRepo(repo);
        a.setPrNumber(n);
        a.setTier(tier);
        a.setSecurityFlag(security);
        a.setSummary("summary line\nmore");
        return a;
    }

    @Test
    void digestIncludesRowAndActionLinksWhenConfigured() {
        EmailTemplate tpl = new EmailTemplate(tokenService(true));
        String body = tpl.renderDigest(List.of(analysis("acme", "api", 7, "RED", false)));

        assertTrue(body.contains("acme/api#7"));
        assertTrue(body.contains("RED"));
        assertTrue(body.contains("Approve"));
        assertTrue(body.contains("https://bot.example.com/action?token="));
    }

    @Test
    void digestOmitsActionLinksWhenTokenUnconfigured() {
        EmailTemplate tpl = new EmailTemplate(tokenService(false));
        String body = tpl.renderDigest(List.of(analysis("acme", "api", 7, "RED", false)));

        assertTrue(body.contains("acme/api#7"));
        assertFalse(body.contains("Approve"));
    }

    @Test
    void alertMarksSecurityAndIncludesLinks() {
        EmailTemplate tpl = new EmailTemplate(tokenService(true));
        String body = tpl.renderAlert(analysis("acme", "api", 7, "YELLOW", true));

        assertTrue(body.contains("SECURITY"));
        assertTrue(body.contains("Approve"));
        assertTrue(body.contains("Reject"));
    }
}
