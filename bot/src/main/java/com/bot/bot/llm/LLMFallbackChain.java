package com.bot.bot.llm;

import com.bot.bot.config.LLMProperties;
import com.bot.bot.config.ProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM client that tries multiple providers in order until one succeeds.
 * Implements the LLMClient interface and delegates to a list of OpenAiCompatibleClient instances.
 */
@Slf4j
public class LLMFallbackChain implements LLMClient, InitializingBean {

    private final LLMProperties llmProperties;
    final List<OpenAiCompatibleClient> delegates = new ArrayList<>();

    public LLMFallbackChain(LLMProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    @Override
    public void afterPropertiesSet() {
        // Build the delegate list from configuration
        List<ProviderConfig> providerConfigs = llmProperties.getProviders();
        if (providerConfigs == null || providerConfigs.isEmpty()) {
            log.warn("No LLM providers configured");
            return;
        }

        for (ProviderConfig config : providerConfigs) {
            OpenAiCompatibleClient client = createClientForConfig(config);
            if (client != null) {
                delegates.add(client);
                log.info("Added LLM client for provider: {} (type: {})", 
                        config.getName(), config.getProviderType());
            } else {
                log.warn("Unknown provider type: {} for config: {}", 
                        config.getProviderType(), config.getName());
            }
        }

        if (delegates.isEmpty()) {
            log.warn("No valid LLM clients could be created from configuration");
        }
    }

    private OpenAiCompatibleClient createClientForConfig(ProviderConfig config) {
        // All providers use the same OpenAiCompatibleClient implementation
        // The differences are handled via configuration (baseUrl, apiKey, model)
        return new OpenAiCompatibleClient(
                config.getBaseUrl(),
                config.getApiKey(),
                config.getModel()
        );
    }

    @Override
    public Mono<String> generateCodeReview(String prompt) {
        if (delegates.isEmpty()) {
            String msg = "No LLM providers configured. Set llm.providers in application.yaml.";
            log.warn(msg);
            return Mono.error(new IllegalStateException(msg));
        }

        // Collect provider names for error reporting
        List<String> providerNames = llmProperties.getProviders().stream()
                .map(ProviderConfig::getName)
                .toList();

        // Build the fallback chain from last to first so Monos are composed correctly.
        // Start with the last provider as the base of the chain.
        int lastIdx = delegates.size() - 1;
        Mono<String> chain = delegates.get(lastIdx).generateCodeReview(prompt);

        // Wrap the last result: if it errors, throw IllegalStateException naming all providers
        String lastName = lastIdx < providerNames.size() ? providerNames.get(lastIdx) : "unknown";
        chain = chain.onErrorResume(e -> {
            log.warn("LLM provider '{}' (last) failed: {}", lastName, e.toString());
            String attempted = String.join(", ", providerNames);
            return Mono.error(new IllegalStateException(
                    "All LLM providers failed: [" + attempted + "]", e));
        });

        // Prepend each earlier provider: if it errors, log and fall through to the next
        for (int i = lastIdx - 1; i >= 0; i--) {
            OpenAiCompatibleClient client = delegates.get(i);
            String providerName = i < providerNames.size() ? providerNames.get(i) : "unknown";
            Mono<String> next = chain; // capture the remainder of the chain
            chain = client.generateCodeReview(prompt)
                    .onErrorResume(e -> {
                        log.warn("LLM provider '{}' failed, falling back: {}", providerName, e.toString());
                        return next;
                    });
        }

        return chain;
    }
}