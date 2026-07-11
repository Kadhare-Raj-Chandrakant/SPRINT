package com.sprint.sprint.config;

import com.sprint.sprint.persistence.MaintainerConfig;
import com.sprint.sprint.persistence.MaintainerConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConfigService {

    private final AppProperties appProperties;
    private final MailProperties mailProperties;
    private final MaintainerConfigRepository maintainerConfigRepository;

    public record ResolvedConfig(
            String installationId,
            String digestCron,
            String thresholdTier,
            boolean labelsEnabled,
            boolean emailEnabled,
            boolean actionsEnabled,
            List<String> maintainerEmails
    ) {}

    public ResolvedConfig resolve(long installationId) {
        return resolve(String.valueOf(installationId));
    }

    public ResolvedConfig resolve(String installationId) {
        String digestCron = mailProperties.getDigestCron();
        String thresholdTier = "RED";
        boolean labelsEnabled = true;
        boolean emailEnabled = mailProperties.isEnabled();
        boolean actionsEnabled = appProperties.getActionSecret() != null && !appProperties.getActionSecret().isBlank();
        List<String> maintainerEmails = new ArrayList<>(mailProperties.getMaintainerEmails());

        Optional<MaintainerConfig> row = maintainerConfigRepository.findById(installationId);
        if (row.isPresent()) {
            MaintainerConfig cfg = row.get();
            if (cfg.getDigestCron() != null) {
                digestCron = cfg.getDigestCron();
            }
            if (cfg.getThresholdTier() != null && !cfg.getThresholdTier().isBlank()) {
                thresholdTier = cfg.getThresholdTier();
            }
            if (cfg.getLabelsEnabled() != null) {
                labelsEnabled = cfg.getLabelsEnabled();
            }
            if (cfg.getEmailEnabled() != null) {
                emailEnabled = cfg.getEmailEnabled();
            }
            if (cfg.getActionsEnabled() != null) {
                actionsEnabled = cfg.getActionsEnabled();
            }
            List<String> cfgEmails = cfg.getMaintainerEmails();
            if (cfgEmails != null && !cfgEmails.isEmpty()) {
                maintainerEmails = new ArrayList<>(cfgEmails);
            }
        }

        return new ResolvedConfig(
                installationId,
                digestCron,
                thresholdTier,
                labelsEnabled,
                emailEnabled,
                actionsEnabled,
                maintainerEmails
        );
    }
}