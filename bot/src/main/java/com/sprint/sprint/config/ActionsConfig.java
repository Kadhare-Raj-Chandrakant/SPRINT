package com.sprint.sprint.config;

import com.sprint.sprint.actions.TokenService;
import com.sprint.sprint.config.ConfigService;
import com.sprint.sprint.email.EmailTemplate;
import com.sprint.sprint.email.MailService;
import com.sprint.sprint.email.ThresholdAlertService;
import com.sprint.sprint.persistence.MetaRepository;
import com.sprint.sprint.persistence.PrAnalysisRepository;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the one-click action token service and the email-rendering/alert beans.
 * {@link TokenService} reads the signing secret from {@link AppProperties}; when
 * no secret is configured, action links are simply omitted from emails.
 */
@Configuration
public class ActionsConfig {

    private static final Logger log = LoggerFactory.getLogger(ActionsConfig.class);

    private final AppProperties appProperties;
    private final MailProperties mailProperties;
    private final ConfigService configService;

    public ActionsConfig(AppProperties appProperties, MailProperties mailProperties, ConfigService configService) {
        this.appProperties = appProperties;
        this.mailProperties = mailProperties;
        this.configService = configService;
    }

    @PostConstruct
    void validateActionConfig() {
        if (Boolean.FALSE.equals(mailProperties.isEnabled())) {
            return;
        }
        boolean missingSecret = appProperties.getActionSecret() == null || appProperties.getActionSecret().isBlank();
        boolean missingBaseUrl = appProperties.getBaseUrl() == null || appProperties.getBaseUrl().isBlank();
        if (missingSecret || missingBaseUrl) {
            log.warn("Mail is enabled but action links disabled: action-secret blank={}, base-url blank={}. "
                    + "Digest/alert emails will omit one-click action buttons.", missingSecret, missingBaseUrl);
        }
    }

    @Bean
    public TokenService tokenService(MetaRepository metaRepository) {
        return new TokenService(appProperties, metaRepository);
    }

    @Bean
    public EmailTemplate emailTemplate(TokenService tokenService) {
        return new EmailTemplate(tokenService);
    }

    @Bean
    public ThresholdAlertService thresholdAlertService(PrAnalysisRepository prAnalysisRepository,
                                                        MailService mailService, ConfigService configService,
                                                        EmailTemplate emailTemplate) {
        return new ThresholdAlertService(prAnalysisRepository, mailService, configService, emailTemplate);
    }
}
