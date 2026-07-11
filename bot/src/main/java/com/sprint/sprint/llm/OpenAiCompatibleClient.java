package com.sprint.sprint.llm;

import com.sprint.sprint.config.LLMProperties;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * LLM client for OpenAI-compatible APIs (including NVIDIA NIM and Ollama's
 * experimental OpenAI-compatible endpoint).
 * Uses the {@code /v1/chat/completions} endpoint with chat message format.
 * <p>
 * Designed for direct instantiation by {@link LLMFallbackChain}.
 */
@Slf4j
public class OpenAiCompatibleClient implements LLMClient {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final WebClient webClient;
    private final Gson gson;
    private final int timeoutSeconds;

    /**
     * Primary constructor used by {@link LLMFallbackChain}.
     */
    public OpenAiCompatibleClient(String baseUrl, String apiKey, String model) {
        this(baseUrl, apiKey, model, 60, null, null);
    }

    /**
     * Full constructor that allows injecting a shared WebClient and Gson
     * (used for Spring bean creation).
     */
    public OpenAiCompatibleClient(String baseUrl, String apiKey, String model,
                                   int timeoutSeconds, WebClient webClient, Gson gson) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.webClient = webClient != null ? webClient : createDefaultWebClient();
        this.gson = gson != null ? gson : new Gson();
    }

    private static WebClient createDefaultWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));
        return WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    @Override
    public Mono<String> generateCodeReview(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return Mono.just("No diff content to review");
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", 0.2);

        String url = baseUrl + "/v1/chat/completions";

        return webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(requestBody.toString())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .map(response -> {
                    try {
                        JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                        JsonArray choices = jsonResponse.getAsJsonArray("choices");
                        if (choices != null && !choices.isJsonNull() && choices.size() > 0) {
                            JsonObject firstChoice = choices.get(0).getAsJsonObject();
                            JsonObject messageObj = firstChoice.getAsJsonObject("message");
                            if (messageObj != null && !messageObj.isJsonNull()) {
                                JsonElement contentElement = messageObj.get("content");
                                if (contentElement != null && !contentElement.isJsonNull()) {
                                    String content = contentElement.getAsString();
                                    return content != null ? content : "";
                                }
                            }
                        }
                        return "";
                    } catch (Exception e) {
                        log.error("Error parsing OpenAI-compatible response", e);
                        return "Error processing LLM response";
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Error calling OpenAI-compatible LLM after retries", e);
                    return Mono.error(e);
                });
    }
}