package com.bot.bot.llm;

import com.bot.bot.config.LLMProperties;
import com.bot.bot.config.WebClientConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * LLM client for Ollama.
 * Uses the {@code /api/generate} endpoint with the configured model.
 * <p>
 * Automatically retries on transient failures (502, network timeouts) with
 * exponential backoff.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
public class OllamaClient implements LLMClient {

    private final LLMProperties llmProperties;
    private final WebClient webClient;
    private final Gson gson;

    public OllamaClient(LLMProperties llmProperties, WebClient webClient, Gson gson) {
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
        requestBody.addProperty("prompt", prompt);
        requestBody.addProperty("stream", false);
        requestBody.addProperty("temperature", 0.7);

        String url = llmProperties.getBaseUrl() + "/api/generate";

        return webClient.post()
                .uri(url)
                .bodyValue(requestBody.toString())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(llmProperties.getTimeoutSeconds()))
                .map(response -> {
                    try {
                        JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                        String text = jsonResponse.get("response").getAsString();
                        return text != null ? text : "";
                    } catch (Exception e) {
                        log.error("Error parsing Ollama response", e);
                        return "Error processing LLM response";
                    }
                })
                .retryWhen(WebClientConfig.buildRetrySpec("ollama-review"))
                .onErrorResume(e -> {
                    log.warn("Error calling Ollama LLM after retries", e);
                    return Mono.just("LLM service unavailable");
                });
    }
}
