package com.sprint.sprint.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private boolean heuristicsEnabled = true;
    private boolean llmEnabled = true;
    private boolean autoApprove = false;
    private boolean inlineComments = true;
    private boolean reviewSummaryEnabled = true;
    private String actionSecret;
    private String baseUrl;
}
