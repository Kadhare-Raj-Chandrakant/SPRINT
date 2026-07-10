package com.bot.bot.llm;

import com.bot.bot.config.LLMProperties;
import com.bot.bot.config.WebClientConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Client for NVIDIA NIM (NVIDIA Inference Microservices) API.
 * Uses the OpenAI-compatible {@code /v1/chat/completions} endpoint.
 * <p>
 * Default base URL: {@code http://localhost:8000}
 * Hosted API: {@code https://integrate.api.nvidia.com/v1} (set as base-url, api-key required)
 * <p>
 * Automatically retries on transient failures with exponential backoff.
 *
 * <p>Requires:
 * <ul>
 *   <li>{@code LLM_PROVIDER=nvidia-nim}</li>
 *   <li>{@code LLM_API_KEY=nvapi-...} (required for hosted API, optional for self-hosted)</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "nvidia-nim", matchIfMissing = true)
public class NvidiaNimClient implements LLMClient {

    private final LLMProperties llmProperties;
    private final WebClient webClient;
    private final Gson gson;

    public NvidiaNimClient(LLMProperties llmProperties, WebClient webClient, Gson gson) {
        this.llmProperties = llmProperties;
        this.webClient = webClient;
        this.gson = gson;
    }

    @Override
    public Mono<String> generateCodeReview(String prompt) {
        if (!llmProperties.isEnabled()) {
            return Mono.just("LLM review disabled");
        }

        if (prompt == null || prompt.isBlank()) {
            return Mono.just("No diff content to review");
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", llmProperties.getModel());

        // Build messages array (OpenAI-compatible chat format)
        JsonArray messages = new JsonArray();

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", "You are an expert code reviewer. Provide concise, actionable feedback.");
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);

        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", 0.7);
        requestBody.addProperty("max_tokens", 2048);

        String url = llmProperties.getBaseUrl() + "/v1/chat/completions";

        return webClient.post()
                .uri(url)
                .headers(headers -> {
                    String apiKey = llmProperties.getApiKey();
                    if (apiKey != null && !apiKey.isEmpty()) {
                        headers.setBearerAuth(apiKey);
                    }
                })
                .bodyValue(requestBody.toString())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(llmProperties.getTimeoutSeconds()))
                .map(response -> {
                    try {
                        JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                        JsonArray choices = jsonResponse.getAsJsonArray("choices");
                        if (choices != null && !choices.isEmpty()) {
                            String content = choices.get(0).getAsJsonObject()
                                    .getAsJsonObject("message")
                                    .get("content")
                                    .getAsString();
                            return content != null ? content : "";
                        }
                        return "No response content";
                    } catch (Exception e) {
                        log.error("Error parsing NVIDIA NIM response", e);
                        return "Error processing LLM response";
                    }
                })
                .retryWhen(WebClientConfig.buildRetrySpec("nvidia-nim-review"))
                .onErrorResume(e -> {
                    log.warn("Error calling NVIDIA NIM LLM after retries", e);
                    return Mono.just("LLM service unavailable");
                });
    }
}
