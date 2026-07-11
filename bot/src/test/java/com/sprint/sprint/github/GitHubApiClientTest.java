package com.sprint.sprint.github;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sprint.sprint.config.GitHubProperties;
import com.sprint.sprint.github.GitHubJwtGenerator;
import com.google.gson.Gson;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GitHubApiClientTest {

    private GitHubApiClient client() {
        GitHubProperties props = new GitHubProperties();
        props.setApiUrl("https://api.github.com");
        GitHubJwtGenerator jwt = mock(GitHubJwtGenerator.class);
        when(jwt.generateAppToken()).thenReturn("jwt");
        return new GitHubApiClient(props, jwt, WebClient.builder().build(), new Gson());
    }

    @Test
    void closePullRequestRejectsInvalidInstallationId() {
        StepVerifier.create(client().closePullRequest("acme", "api", 7, 0))
                .expectError()
                .verify(Duration.ofSeconds(10));
    }

    @Test
    void submitReviewRejectsInvalidInstallationId() {
        StepVerifier.create(client().submitReview("acme", "api", 7, "body", "APPROVE", null, 0))
                .expectError()
                .verify(Duration.ofSeconds(10));
    }

    @Test
    void getInstallationTokenRejectsInvalidInstallationId() {
        StepVerifier.create(client().getInstallationToken(0))
                .expectError()
                .verify(Duration.ofSeconds(10));
    }
}
