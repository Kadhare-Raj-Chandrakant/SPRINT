package com.bot.bot.config;

import lombok.Data;

/**
 * Configuration for a single LLM provider instance.
 * Multiple providers can be configured in the {@code llm.providers} list.
 */
@Data
public class ProviderConfig {
    /** Provider name (e.g., "openai-compatible", "nvidia-nim", "ollama"). */
    private String providerType = "openai-compatible";

    /** Human-readable label for this provider (used in logs and error messages). */
    private String name = "";

    /** Base URL for the API endpoint. */
    private String baseUrl = "http://localhost:8000";

    /** Model identifier. */
    private String model = "gpt-4o-mini";

    /** API key (optional, for providers that require authentication). */
    private String apiKey = "";
}