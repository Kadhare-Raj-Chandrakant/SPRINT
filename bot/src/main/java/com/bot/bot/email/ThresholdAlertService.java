package com.bot.bot.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.bot.bot.config.ConfigService;
import com.bot.bot.persistence.PrAnalysis;
import com.bot.bot.persistence.PrAnalysisRepository;

import java.util.List;

/**
 * Sends an immediate email alert when a PR analysis lands at or above the
 * installation's configured threshold tier or carries a security flag. Deduped
 * via the {@code alerted} column so each PR is alerted at most once. No-op when
 * the installation has email disabled or the PR is not urgent.
 */
@Service
public class ThresholdAlertService {
    private static final Logger log = LoggerFactory.getLogger(ThresholdAlertService.class);

    private static final List<String> TIER_RANK = List.of("GREEN", "YELLOW", "RED");

    private final PrAnalysisRepository prAnalysisRepository;
    private final MailService mailService;
    private final EmailTemplate emailTemplate;
    private final ConfigService configService;

    public ThresholdAlertService(PrAnalysisRepository prAnalysisRepository, MailService mailService,
                                 ConfigService configService, EmailTemplate emailTemplate) {
        this.prAnalysisRepository = prAnalysisRepository;
        this.mailService = mailService;
        this.configService = configService;
        this.emailTemplate = emailTemplate;
    }

    public void maybeAlert(PrAnalysis analysis) {
        ConfigService.ResolvedConfig cfg = configService.resolve(
                analysis.getInstallationId() == null ? "" : analysis.getInstallationId());
        if (!cfg.emailEnabled()) {
            return;
        }
        if (!isUrgent(analysis, cfg.thresholdTier())) {
            return;
        }
        if (Boolean.TRUE.equals(analysis.getAlerted())) {
            return;
        }
        mailService.sendEmail(cfg.maintainerEmails(),
                "PR Triage Alert — " + analysis.getOwner() + "/" + analysis.getRepo()
                        + "#" + analysis.getPrNumber(),
                emailTemplate.renderAlert(analysis));
        analysis.setAlerted(true);
        prAnalysisRepository.save(analysis);
        log.info("Alerted maintainers for urgent PR {}/{}#{}",
                analysis.getOwner(), analysis.getRepo(), analysis.getPrNumber());
    }

    private boolean isUrgent(PrAnalysis a, String thresholdTier) {
        if (Boolean.TRUE.equals(a.getSecurityFlag())) {
            return true;
        }
        if (a.getTier() == null) {
            return false;
        }
        // Alert when the PR's tier ranks at or above the configured threshold.
        return rank(a.getTier()) >= rank(thresholdTier);
    }

    private int rank(String tier) {
        int i = TIER_RANK.indexOf(tier == null ? "" : tier.toUpperCase());
        return i < 0 ? rank("YELLOW") : i; // unknown tier treated as YELLOW
    }
}
