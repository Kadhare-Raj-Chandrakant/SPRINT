package com.bot.bot.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import lombok.Data;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "llm")
public class LLMProperties {

    /**
     * LLM provider: "ollama" or "nvidia-nim".
     */
    @NotEmpty
    @Pattern(regexp = "ollama|nvidia-nim", message = "LLM_PROVIDER must be 'ollama' or 'nvidia-nim'")
    private String provider = "nvidia-nim";

    @NotEmpty(message = "LLM_MODEL must be set")
    private String model = "meta/llama-3.1-8b-instruct";

    @NotEmpty(message = "LLM_BASE_URL must be set")
    private String baseUrl = "http://localhost:8000";

    @Min(value = 1, message = "LLM_TIMEOUT_SECONDS must be >= 1")
    private int timeoutSeconds = 60;

    private boolean enabled = true;

    /** API key for providers that require one (e.g., NVIDIA NIM) */
    private String apiKey = "";
}
