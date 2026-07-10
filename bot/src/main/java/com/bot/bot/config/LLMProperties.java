package com.bot.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "llm")
public class LLMProperties {

    /**
     * Ordered list of LLM provider configurations.
     * The first provider is tried first; subsequent providers serve as fallbacks.
     * Expected provider types: "openai-compatible", "nvidia-nim", "ollama".
     * <p>
     * Example configuration:
     * <pre>
     * llm:
     *   providers:
     *     - provider-type: openai-compatible
     *       name: openai
     *       base-url: https://api.openai.com
     *       api-key: ${OPENAI_API_KEY}
     *       model: gpt-4o-mini
     *     - provider-type: nvidia-nim
     *       name: nvidia
     *       base-url: http://localhost:8000
     *       api-key: ${NVIDIA_API_KEY}
     *       model: meta/llama-3.1-8b-instruct
     *     - provider-type: ollama
     *       name: ollama
     *       base-url: http://localhost:11434
     *       model: llama3
     * </pre>
     */
    private List<ProviderConfig> providers = new ArrayList<>();

    @Min(value = 1, message = "LLM_TIMEOUT_SECONDS must be >= 1")
    private int timeoutSeconds = 60;

    private boolean enabled = true;
}