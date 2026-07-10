package com.bot.bot.health;

import com.bot.bot.config.LLMProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Probes the configured LLM provider to verify it is reachable and responsive.
 * <ul>
 *   <li>Ollama: {@code GET /api/tags} → returns list of installed models</li>
 *   <li>NVIDIA NIM: {@code GET /v1/models} → returns available models</li>
 * </ul>
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
                    .withDetail("provider", llmProperties.getProvider())
                    .withDetail("status", "LLM disabled by configuration")
                    .build();
        }

        try {
            String url = buildHealthUrl();
            webClient.get()
                    .uri(url)
                    .headers(headers -> {
                        if (llmProperties.getApiKey() != null && !llmProperties.getApiKey().isEmpty()) {
                            headers.setBearerAuth(llmProperties.getApiKey());
                        }
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();

            return Health.up()
                    .withDetail("provider", llmProperties.getProvider())
                    .withDetail("baseUrl", llmProperties.getBaseUrl())
                    .withDetail("status", "reachable")
                    .build();

        } catch (Exception e) {
            log.warn("LLM health check failed for {} at {}: {}",
                    llmProperties.getProvider(), llmProperties.getBaseUrl(), e.getMessage());
            return Health.down()
                    .withDetail("provider", llmProperties.getProvider())
                    .withDetail("baseUrl", llmProperties.getBaseUrl())
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }

    private String buildHealthUrl() {
        String base = llmProperties.getBaseUrl().replaceAll("/+$", "");
        return switch (llmProperties.getProvider()) {
            case "ollama" -> base + "/api/tags";
            case "nvidia-nim" -> base + "/v1/models";
            default -> base + "/health";
        };
    }
}
