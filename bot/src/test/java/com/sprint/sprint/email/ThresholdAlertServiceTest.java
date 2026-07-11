package com.sprint.sprint.email;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sprint.sprint.config.ConfigService;
import com.sprint.sprint.persistence.PrAnalysis;
import com.sprint.sprint.persistence.PrAnalysisRepository;

import org.junit.jupiter.api.Test;

import java.util.List;

class ThresholdAlertServiceTest {

    private PrAnalysis urgent(String tier, boolean security, boolean alerted) {
        PrAnalysis a = new PrAnalysis();
        a.setOwner("acme");
        a.setRepo("api");
        a.setPrNumber(7);
        a.setTier(tier);
        a.setSecurityFlag(security);
        a.setAlerted(alerted);
        return a;
    }

    private ConfigService config(String thresholdTier, boolean emailEnabled) {
        ConfigService c = mock(ConfigService.class);
        when(c.resolve(anyString())).thenReturn(new ConfigService.ResolvedConfig(
                "", "0 0 18 * * *", thresholdTier, true, emailEnabled, true, List.of("dev@example.com")));
        return c;
    }

    @Test
    void alertsOnRedTierAndMarksAlerted() {
        PrAnalysisRepository repo = mock(PrAnalysisRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MailService mail = mock(MailService.class);
        EmailTemplate tpl = mock(EmailTemplate.class);
        when(tpl.renderAlert(any())).thenReturn("<alert/>");

        PrAnalysis a = urgent("RED", false, false);
        new ThresholdAlertService(repo, mail, config("RED", true), tpl).maybeAlert(a);

        verify(mail).sendEmail(anyList(), any(), any());
        verify(repo).save(any(PrAnalysis.class));
        assertTrue(a.getAlerted());
    }

    @Test
    void noAlertWhenMailDisabled() {
        PrAnalysisRepository repo = mock(PrAnalysisRepository.class);
        MailService mail = mock(MailService.class);
        EmailTemplate tpl = mock(EmailTemplate.class);

        new ThresholdAlertService(repo, mail, config("RED", false), tpl).maybeAlert(urgent("RED", false, false));

        verify(mail, never()).sendEmail(anyList(), any(), any());
        verify(repo, never()).save(any());
    }

    @Test
    void noAlertWhenBelowThreshold() {
        PrAnalysisRepository repo = mock(PrAnalysisRepository.class);
        MailService mail = mock(MailService.class);
        EmailTemplate tpl = mock(EmailTemplate.class);

        // Threshold is RED, PR is GREEN and not security-flagged -> not urgent.
        new ThresholdAlertService(repo, mail, config("RED", true), tpl).maybeAlert(urgent("GREEN", false, false));

        verify(mail, never()).sendEmail(anyList(), any(), any());
    }

    @Test
    void alertsYellowWhenThresholdYellow() {
        PrAnalysisRepository repo = mock(PrAnalysisRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MailService mail = mock(MailService.class);
        EmailTemplate tpl = mock(EmailTemplate.class);
        when(tpl.renderAlert(any())).thenReturn("<alert/>");

        new ThresholdAlertService(repo, mail, config("YELLOW", true), tpl).maybeAlert(urgent("YELLOW", false, false));

        verify(mail).sendEmail(anyList(), any(), any());
    }

    @Test
    void noSecondAlertWhenAlreadyAlerted() {
        PrAnalysisRepository repo = mock(PrAnalysisRepository.class);
        MailService mail = mock(MailService.class);
        EmailTemplate tpl = mock(EmailTemplate.class);

        new ThresholdAlertService(repo, mail, config("RED", true), tpl).maybeAlert(urgent("RED", false, true));

        verify(mail, never()).sendEmail(anyList(), any(), any());
    }

    @Test
    void alertsOnSecurityFlagEvenIfNotRed() {
        PrAnalysisRepository repo = mock(PrAnalysisRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MailService mail = mock(MailService.class);
        EmailTemplate tpl = mock(EmailTemplate.class);
        when(tpl.renderAlert(any())).thenReturn("<alert/>");

        new ThresholdAlertService(repo, mail, config("RED", true), tpl).maybeAlert(urgent("YELLOW", true, false));

        verify(mail).sendEmail(anyList(), any(), any());
    }
}
