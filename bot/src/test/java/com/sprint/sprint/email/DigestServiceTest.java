package com.sprint.sprint.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sprint.sprint.config.ConfigService;
import com.sprint.sprint.email.EmailTemplate;
import com.sprint.sprint.persistence.Meta;
import com.sprint.sprint.persistence.PrAnalysis;
import com.sprint.sprint.persistence.PrAnalysisRepository;
import com.sprint.sprint.persistence.MetaRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DigestServiceTest {

    @Mock PrAnalysisRepository prAnalysisRepository;
    @Mock MetaRepository metaRepository;
    @Mock MailService mailService;
    @Mock ConfigService configService;
    @Mock EmailTemplate emailTemplate;

    private final Clock clock = Clock.fixed(Instant.parse("2024-01-15T18:00:00Z"), ZoneOffset.UTC);
    private DigestService digestService;

    @BeforeEach
    void setup() {
        digestService = new DigestService(prAnalysisRepository, metaRepository, mailService,
                configService, emailTemplate, clock);
    }

    private PrAnalysis analysis(String owner, String repo, int n, String tier) {
        PrAnalysis a = new PrAnalysis();
        a.setOwner(owner);
        a.setRepo(repo);
        a.setPrNumber(n);
        a.setTier(tier);
        a.setSecurityFlag(false);
        a.setSummary("First line of summary\nDetails...");
        a.setCreatedAt(Instant.parse("2024-01-15T17:00:00Z"));
        return a;
    }

    private void stubConfig(boolean emailEnabled) {
        when(configService.resolve(anyString())).thenReturn(new ConfigService.ResolvedConfig(
                "", "0 0 18 * * *", "RED", true, emailEnabled, true, List.of("dev@example.com")));
    }

    private Meta markerAt(Instant t) {
        Meta m = new Meta();
        m.setKey("lastDigestAt");
        m.setValue(t.toString());
        return m;
    }

    @Test
    void sendsDigestForNewAnalysesAndAdvancesMarker() {
        stubConfig(true);
        when(metaRepository.findById("lastDigestAt")).thenReturn(Optional.of(markerAt(Instant.parse("2024-01-15T12:00:00Z"))));
        when(emailTemplate.renderDigest(anyList())).thenReturn("<table>body</table>");
        List<PrAnalysis> three = List.of(
                analysis("o", "r", 1, "GREEN"),
                analysis("o", "r", 2, "YELLOW"),
                analysis("o", "r", 3, "RED"));
        when(prAnalysisRepository.findByCreatedAtAfter(any(Instant.class))).thenReturn(three);

        digestService.sendDailyDigest();

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendEmail(anyList(), subject.capture(), body.capture());
        assertTrue(subject.getValue().contains("3 update(s)"));
        assertTrue(body.getValue().contains("body"));
        verify(metaRepository).save(any(Meta.class));
    }

    @Test
    void noEmailWhenNoNewAnalyses() {
        when(metaRepository.findById("lastDigestAt")).thenReturn(Optional.of(markerAt(Instant.parse("2024-01-15T12:00:00Z"))));
        when(prAnalysisRepository.findByCreatedAtAfter(any(Instant.class))).thenReturn(List.of());

        digestService.sendDailyDigest();

        verify(mailService, never()).sendEmail(anyList(), anyString(), anyString());
        verify(metaRepository, never()).save(any(Meta.class));
    }

    @Test
    void noEmailWhenInstallationEmailDisabled() {
        stubConfig(false);
        when(metaRepository.findById("lastDigestAt")).thenReturn(Optional.of(markerAt(Instant.parse("2024-01-15T12:00:00Z"))));
        when(prAnalysisRepository.findByCreatedAtAfter(any(Instant.class)))
                .thenReturn(List.of(analysis("o", "r", 1, "RED")));

        digestService.sendDailyDigest();

        verify(mailService, never()).sendEmail(anyList(), anyString(), anyString());
    }

    @Test
    void firstRunSeedsMarkerAndSkipsBackfill() {
        when(metaRepository.findById("lastDigestAt")).thenReturn(Optional.empty());

        digestService.sendDailyDigest();

        verify(mailService, never()).sendEmail(anyList(), anyString(), anyString());
        verify(metaRepository).save(any(Meta.class));
    }

    @Test
    void buildsOneRowPerAnalysis() {
        List<PrAnalysis> two = List.of(analysis("acme", "api", 7, "RED"), analysis("acme", "web", 9, "GREEN"));
        when(emailTemplate.renderDigest(two)).thenReturn(
                "<tr>acme/api#7</tr><tr>acme/web#9</tr>");
        String body = digestService.buildDigestBody(two);
        assertTrue(body.contains("acme/api#7"));
        assertTrue(body.contains("acme/web#9"));
    }
}
