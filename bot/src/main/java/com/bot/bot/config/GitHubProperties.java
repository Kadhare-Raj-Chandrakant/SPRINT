package com.bot.bot.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import lombok.Data;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {

    @NotEmpty(message = "GITHUB_APP_ID must be set")
    private String appId;

    @NotEmpty(message = "GITHUB_CLIENT_ID must be set")
    private String clientId;

    @NotEmpty(message = "GITHUB_WEBHOOK_SECRET must be set")
    private String webhookSecret;

    @NotEmpty(message = "GITHUB_PRIVATE_KEY_PATH must be set")
    private String privateKeyPath;

    private String apiUrl = "https://api.github.com";
}
