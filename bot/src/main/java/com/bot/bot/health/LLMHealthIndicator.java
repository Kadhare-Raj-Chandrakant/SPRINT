package com.bot.bot.health;

import com.bot.bot.config.LLMProperties;
import com.bot.bot.config.ProviderConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * Probes the first configured LLM provider to verify it is reachable and responsive.
 * Uses the OpenAI-compatible {@code GET /v1/models} endpoint for all providers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LLMHealthIndicator implements HealthIndicator {

    private final LLMProperties llmProperties;
    private final WebClient webClient;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Override
    public Health health() {
        if (!llmProperties.isEnabled()) {
            return Health.up()
                    .withDetail("status", "LLM disabled by configuration")
                    .build();
        }

        List<ProviderConfig> providers = llmProperties.getProviders();
        if (providers == null || providers.isEmpty()) {
            return Health.down()
                    .withDetail("status", "No LLM providers configured")
                    .build();
        }

        ProviderConfig primary = providers.get(0);
        try {
            String url = primary.getBaseUrl().replaceAll("/+$", "") + "/v1/models";
            webClient.get()
                    .uri(url)
                    .headers(headers -> {
                        if (primary.getApiKey() != null && !primary.getApiKey().isEmpty()) {
                            headers.setBearerAuth(primary.getApiKey());
                        }
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();

            return Health.up()
                    .withDetail("provider", primary.getName())
                    .withDetail("baseUrl", primary.getBaseUrl())
                    .withDetail("status", "reachable")
                    .build();

        } catch (Exception e) {
            log.warn("LLM health check failed for {} at {}: {}",
                    primary.getName(), primary.getBaseUrl(), e.getMessage());
            return Health.down()
                    .withDetail("provider", primary.getName())
                    .withDetail("baseUrl", primary.getBaseUrl())
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }
}