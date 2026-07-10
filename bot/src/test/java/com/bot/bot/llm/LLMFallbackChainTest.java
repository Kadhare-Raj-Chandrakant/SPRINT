package com.bot.bot.llm;

import com.bot.bot.config.LLMProperties;
import com.bot.bot.config.ProviderConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

/**
 * Unit tests for {@link LLMFallbackChain}.
 * Uses fake OpenAiCompatibleClient stubs to verify fallback behavior.
 */
class LLMFallbackChainTest {

    private LLMProperties props1;
    private LLMProperties props2;
    private LLMProperties props3;

    @BeforeEach
    void setUp() {
        props1 = new LLMProperties();
        props2 = new LLMProperties();
        props3 = new LLMProperties();
    }

    // ── Stub helpers ────────────────────────────────────────────────

    private static OpenAiCompatibleClient stubSuccess(String text) {
        return new OpenAiCompatibleClient("http://stub.local", "", "stub-model") {
            @Override
            public Mono<String> generateCodeReview(String prompt) {
                return Mono.just(text);
            }
        };
    }

    private static OpenAiCompatibleClient stubFailure() {
        return new OpenAiCompatibleClient("http://stub.local", "", "stub-model") {
            @Override
            public Mono<String> generateCodeReview(String prompt) {
                return Mono.error(new RuntimeException("Simulated failure"));
            }
        };
    }

    // ── Test cases ──────────────────────────────────────────────────

    @Test
    void firstSucceeds_othersUntouched() {
        ProviderConfig pc = new ProviderConfig();
        pc.setName("primary");
        pc.setBaseUrl("http://stub.local");
        pc.setApiKey("");
        pc.setModel("stub-model");
        props1.setProviders(List.of(pc));

        LLMFallbackChain chain = new LLMFallbackChain(props1);
        chain.afterPropertiesSet();
        // replace delegates with stubs
        chain.delegates.clear();
        chain.delegates.add(stubSuccess("result-from-first"));
        chain.delegates.add(stubFailure());

        StepVerifier.create(chain.generateCodeReview("test"))
                .expectNext("result-from-first")
                .verifyComplete();
    }

    @Test
    void firstAndSecondFail_thirdSucceeds() {
        ProviderConfig pc1 = new ProviderConfig(); pc1.setName("p1"); pc1.setBaseUrl("http://stub.local"); pc1.setApiKey(""); pc1.setModel("stub-model");
        ProviderConfig pc2 = new ProviderConfig(); pc2.setName("p2"); pc2.setBaseUrl("http://stub.local"); pc2.setApiKey(""); pc2.setModel("stub-model");
        ProviderConfig pc3 = new ProviderConfig(); pc3.setName("p3"); pc3.setBaseUrl("http://stub.local"); pc3.setApiKey(""); pc3.setModel("stub-model");
        props1.setProviders(List.of(pc1, pc2, pc3));

        LLMFallbackChain chain = new LLMFallbackChain(props1);
        chain.afterPropertiesSet();
        chain.delegates.clear();
        chain.delegates.add(stubFailure());
        chain.delegates.add(stubFailure());
        chain.delegates.add(stubSuccess("result-from-third"));

        StepVerifier.create(chain.generateCodeReview("test"))
                .expectNext("result-from-third")
                .verifyComplete();
    }

    @Test
    void allFail_throwsIllegalStateException() {
        ProviderConfig pc1 = new ProviderConfig(); pc1.setName("p1"); pc1.setBaseUrl("http://stub.local"); pc1.setApiKey(""); pc1.setModel("stub-model");
        ProviderConfig pc2 = new ProviderConfig(); pc2.setName("p2"); pc2.setBaseUrl("http://stub.local"); pc2.setApiKey(""); pc2.setModel("stub-model");
        props1.setProviders(List.of(pc1, pc2));

        LLMFallbackChain chain = new LLMFallbackChain(props1);
        chain.afterPropertiesSet();
        chain.delegates.clear();
        chain.delegates.add(stubFailure());
        chain.delegates.add(stubFailure());

        StepVerifier.create(chain.generateCodeReview("test"))
                .expectErrorMatches(e -> e instanceof IllegalStateException
                        && e.getMessage().contains("All LLM providers failed")
                        && e.getMessage().contains("p1")
                        && e.getMessage().contains("p2"))
                .verify();
    }

    @Test
    void emptyDelegates_throwsIllegalStateException() {
        props1.setProviders(List.of());
        LLMFallbackChain chain = new LLMFallbackChain(props1);
        chain.afterPropertiesSet();

        StepVerifier.create(chain.generateCodeReview("test"))
                .expectError(IllegalStateException.class)
                .verify();
    }
}