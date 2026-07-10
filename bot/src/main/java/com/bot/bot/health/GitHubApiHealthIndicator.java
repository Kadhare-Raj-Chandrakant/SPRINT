package com.bot.bot.health;

import com.bot.bot.config.GitHubProperties;
import com.bot.bot.github.GitHubJwtGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Probes the GitHub API using the App JWT to verify:
 * <ul>
 *   <li>Network connectivity to api.github.com</li>
 *   <li>JWT generator produces valid tokens</li>
 *   <li>GitHub App credentials are valid</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubApiHealthIndicator implements HealthIndicator {

    private final GitHubProperties gitHubProperties;
    private final GitHubJwtGenerator jwtGenerator;
    private final WebClient webClient;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Override
    public Health health() {
        try {
            String jwt = jwtGenerator.generateAppToken();
            String url = gitHubProperties.getApiUrl() + "/app";

            String response = webClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + jwt)
                    .header("Accept", "application/vnd.github.v3+json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response != null && response.contains("\"id\"")) {
                return Health.up()
                        .withDetail("apiUrl", gitHubProperties.getApiUrl())
                        .withDetail("status", "GitHub App authenticated successfully")
                        .build();
            }

            return Health.down()
                    .withDetail("apiUrl", gitHubProperties.getApiUrl())
                    .withDetail("reason", "Unexpected response format")
                    .build();

        } catch (Exception e) {
            log.warn("GitHub API health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("apiUrl", gitHubProperties.getApiUrl())
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }
}
