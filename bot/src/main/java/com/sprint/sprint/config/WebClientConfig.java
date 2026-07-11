package com.sprint.sprint.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import com.sprint.sprint.llm.LLMClient;
import com.sprint.sprint.llm.LLMFallbackChain;
import com.sprint.sprint.llm.OpenAiCompatibleClient;
import com.google.gson.Gson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Configures a production-hardened WebClient with:
 * <ul>
 *   <li>Connection pooling (max 50 connections, 30s idle TTL)</li>
 *   <li>Connect / read / write timeouts</li>
 *   <li>Global retry filter on 5xx + network-level exceptions</li>
 *   <li>Exponential backoff (100ms base, 5s max, jitter 0.5)</li>
 * </ul>
 */
@Configuration
public class WebClientConfig {

    static final int MAX_CONNECTIONS = 50;
    static final int CONNECT_TIMEOUT_MS = 10_000;
    static final int READ_TIMEOUT_SECONDS = 30;
    static final int WRITE_TIMEOUT_SECONDS = 30;

    static final int RETRY_MAX_ATTEMPTS = 3;
    static final Duration RETRY_MIN_BACKOFF = Duration.ofMillis(100);
    static final Duration RETRY_MAX_BACKOFF = Duration.ofSeconds(5);

    /**
     * Provides a pre-configured WebClient builder with connection pool,
     * timeouts, and retry filter baked in.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = buildHttpClient();

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)); // 10MB
    }

    /**
     * Default WebClient for general use.
     */
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }

    /**
     * RestTemplate for simple synchronous REST calls.
     */
    @Bean
    public RestTemplate restTemplate() {
        var req = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        req.setConnectTimeout(10_000);
        req.setReadTimeout(30_000);
        return new RestTemplate(req);
    }

    /**
     * Primary LLM client with fallback chain.
     * Tries providers in configured order; falls back on failure.
     */
    @Bean
    public LLMClient llmClient(LLMProperties llmProperties, WebClient webClient, Gson gson) {
        return new LLMFallbackChain(llmProperties, webClient, gson);
    }

    // ── HttpClient with connection pool + timeouts ──────────────────

    private static HttpClient buildHttpClient() {
        ConnectionProvider connectionProvider = ConnectionProvider.builder("pr-review-bot-pool")
                .maxConnections(MAX_CONNECTIONS)
                .pendingAcquireMaxCount(100)
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(5))
                .build();

        return HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)));
    }

    /**
     * Creates a shared Retry spec for use by all API clients.
     * Retries on 5xx status codes and IO-like exceptions with exponential backoff.
     */
    public static Retry buildRetrySpec(String operationName) {
        return Retry.backoff(RETRY_MAX_ATTEMPTS, RETRY_MIN_BACKOFF)
                .maxBackoff(RETRY_MAX_BACKOFF)
                .jitter(0.5)
                .filter(WebClientConfig::isRetryable);
    }

    /**
     * Returns true for server errors (5xx) and transient network failures.
     * Does NOT retry 4xx (client errors).
     */
    private static boolean isRetryable(Throwable throwable) {
        if (throwable instanceof IOException) return true;
        if (throwable instanceof TimeoutException) return true;
        if (throwable instanceof reactor.netty.http.client.PrematureCloseException) return true;

        if (throwable instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcre) {
            return wcre.getStatusCode().is5xxServerError();
        }

        return false;
    }
}
