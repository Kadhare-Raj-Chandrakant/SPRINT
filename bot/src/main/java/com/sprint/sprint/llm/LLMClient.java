package com.sprint.sprint.llm;

import reactor.core.publisher.Mono;

/**
 * Common interface for LLM-based code review providers.
 * Implementations handle provider-specific API formats and authentication.
 */
public interface LLMClient {
    /**
     * Generate a code review for the given prompt.
     *
     * @param prompt the review prompt with code context
     * @return the LLM response text, or an error message string on failure
     */
    Mono<String> generateCodeReview(String prompt);
}
