package com.sprint.sprint.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.sprint.sprint.config.ConfigService;
import com.sprint.sprint.persistence.Meta;
import com.sprint.sprint.persistence.MetaRepository;
import com.sprint.sprint.persistence.PrAnalysis;
import com.sprint.sprint.persistence.PrAnalysisRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds and sends the daily digest email grouping PRs analyzed since the last
 * digest run. Analyses are grouped per installation and each installation's own
 * config (enabled flag + maintainer emails) drives its digest. No-op when there
 * are no new analyses.
 */
@Service
public class DigestService {
    private static final Logger log = LoggerFactory.getLogger(DigestService.class);
    private static final String LAST_DIGEST_KEY = "lastDigestAt";

    private final PrAnalysisRepository prAnalysisRepository;
    private final MetaRepository metaRepository;
    private final MailService mailService;
    private final EmailTemplate emailTemplate;
    private final Clock clock;
    private final ConfigService configService;

    public DigestService(PrAnalysisRepository prAnalysisRepository, MetaRepository metaRepository,
                          MailService mailService, ConfigService configService,
                          EmailTemplate emailTemplate, Clock clock) {
        this.prAnalysisRepository = prAnalysisRepository;
        this.metaRepository = metaRepository;
        this.mailService = mailService;
        this.configService = configService;
        this.emailTemplate = emailTemplate;
        this.clock = clock;
    }

    @Autowired
    public DigestService(PrAnalysisRepository prAnalysisRepository, MetaRepository metaRepository,
                          MailService mailService, ConfigService configService,
                          EmailTemplate emailTemplate) {
        this(prAnalysisRepository, metaRepository, mailService, configService, emailTemplate, Clock.systemUTC());
    }

    @Scheduled(cron = "${app.mail.digest-cron:0 0 18 * * *}")
    public void sendDailyDigest() {
        Instant lastDigest = getLastDigestAt();

        // First run: don't backfill the entire history. Seed the marker and return.
        if (lastDigest.equals(Instant.EPOCH)) {
            setLastDigestAt(Instant.now(clock));
            log.info("Digest first run — seeding lastDigestAt, skipping backfill");
            return;
        }

        Instant now = Instant.now(clock);
        List<PrAnalysis> newAnalyses = prAnalysisRepository.findByCreatedAtAfter(lastDigest);
        if (newAnalyses.isEmpty()) {
            log.info("Digest skipped - no new analyses since {}", lastDigest);
            return;
        }
        Map<String, List<PrAnalysis>> byInstallation = newAnalyses.stream()
                .collect(Collectors.groupingBy(a -> a.getInstallationId() == null ? "" : a.getInstallationId()));
        boolean anySent = false;
        for (Map.Entry<String, List<PrAnalysis>> entry : byInstallation.entrySet()) {
            ConfigService.ResolvedConfig cfg = configService.resolve(entry.getKey());
            if (!cfg.emailEnabled()) {
                log.debug("Digest skipped for installation {} - email disabled", entry.getKey());
                continue;
            }
            try {
                String body = emailTemplate.renderDigest(entry.getValue());
                mailService.sendEmail(cfg.maintainerEmails(),
                        "SPRINT Digest — " + entry.getValue().size() + " update(s)", body);
                log.info("Digested {} analysis(es) for installation {}", entry.getValue().size(), entry.getKey());
                anySent = true;
            } catch (Exception e) {
                // Best-effort: a failure for one installation must not skip the rest,
                // and we don't advance lastDigestAt so it can be retried next run.
                log.error("Failed to send digest for installation {}", entry.getKey(), e);
            }
        }
        if (anySent) {
            setLastDigestAt(now);
        }
    }

    String buildDigestBody(List<PrAnalysis> analyses) {
        return emailTemplate.renderDigest(analyses);
    }

    private Instant getLastDigestAt() {
        return metaRepository.findById(LAST_DIGEST_KEY)
                .map(m -> Instant.parse(m.getValue()))
                .orElse(Instant.EPOCH);
    }

    private void setLastDigestAt(Instant t) {
        Meta meta = metaRepository.findById(LAST_DIGEST_KEY).orElse(new Meta());
        meta.setKey(LAST_DIGEST_KEY);
        meta.setValue(t.toString());
        metaRepository.save(meta);
    }
}
