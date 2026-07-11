package com.sprint.sprint.github;

import com.sprint.sprint.config.GitHubProperties;
import com.sprint.sprint.config.WebClientConfig;
import com.sprint.sprint.domain.PullRequestContext;
import com.sprint.sprint.domain.ReviewComment;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client for the GitHub REST API.
 * <p>
 * All authenticated API calls use an installation access token obtained by
 * exchanging the App JWT. The token is cached per installation ID for 55 minutes.
 * <p>
 * Transient failures (5xx, network timeouts) are retried automatically
 * via {@link WebClientConfig#buildRetrySpec(String)} with exponential backoff.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubApiClient {

    private final GitHubProperties gitHubProperties;
    private final GitHubJwtGenerator jwtGenerator;
    private final WebClient webClient;
    private final Gson gson;

    // Per-installation cache: a GitHub App often serves multiple orgs, so a
    // single-slot token cache would let concurrent requests clobber each other.
    private final Map<Long, CachedToken> installationTokenCache = new ConcurrentHashMap<>();
    private static final Duration TOKEN_CACHE_TTL = Duration.ofMinutes(55);

    private record CachedToken(String token, Instant expiry) {
        boolean isValid() {
            return Instant.now().isBefore(expiry);
        }
    }

    /**
     * Parse PR metadata from webhook payload (no API calls).
     * Extracts installation ID for subsequent authenticated calls.
     */
    public PullRequestContext fetchPullRequestContext(JsonObject prData) {
        JsonObject repo = prData.getAsJsonObject("repository");
        JsonObject pr = prData.getAsJsonObject("pull_request");
        if (repo == null || pr == null) {
            throw new IllegalArgumentException("Missing repository or pull_request in webhook payload");
        }

        JsonObject repoOwner = repo.getAsJsonObject("owner");
        if (repoOwner == null) {
            throw new IllegalArgumentException("Missing repository.owner in webhook payload");
        }

        // Extract installation ID from webhook payload
        long installationId = 0;
        JsonObject installation = prData.getAsJsonObject("installation");
        if (installation != null && installation.has("id") && !installation.get("id").isJsonNull()) {
            installationId = installation.get("id").getAsLong();
        }

        String owner = repoOwner.get("login").getAsString();
        String repoName = repo.get("name").getAsString();
        int prNumber = pr.get("number").getAsInt();
        String title = pr.get("title").getAsString();
        String description = pr.has("body") && !pr.get("body").isJsonNull() ? pr.get("body").getAsString() : "";
        JsonObject prUser = pr.has("user") && !pr.get("user").isJsonNull() ? pr.getAsJsonObject("user") : null;
        String authorLogin = prUser != null && prUser.has("login") && !prUser.get("login").isJsonNull()
                ? prUser.get("login").getAsString() : "unknown";

        JsonObject base = pr.getAsJsonObject("base");
        JsonObject head = pr.getAsJsonObject("head");
        if (base == null || head == null) {
            throw new IllegalArgumentException("Missing pull_request.base or pull_request.head in webhook payload");
        }

        String baseRef = base.get("ref").getAsString();
        String headRef = head.get("ref").getAsString();
        String commitSha = head.get("sha").getAsString();

        return PullRequestContext.builder()
                .owner(owner)
                .repo(repoName)
                .prNumber(prNumber)
                .title(title)
                .description(description)
                .authorLogin(authorLogin)
                .baseRef(baseRef)
                .headRef(headRef)
                .commitSha(commitSha)
                .installationId(installationId)
                .build();
    }

    /**
     * Exchange the App JWT for an installation access token.
     * Cached per installation ID for 55 minutes (tokens expire after 1 hour).
     */
    public Mono<String> getInstallationToken(long installationId) {
        if (installationId <= 0) {
            return Mono.error(new IllegalArgumentException("Invalid installation ID: " + installationId));
        }

        // Return cached token if still valid for this installation
        CachedToken cached = installationTokenCache.get(installationId);
        if (cached != null && cached.isValid()) {
            return Mono.just(cached.token());
        }

        String url = gitHubProperties.getApiUrl() + "/app/installations/" + installationId + "/access_tokens";

        return webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + jwtGenerator.generateAppToken())
                .header("Accept", "application/vnd.github.v3+json")
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    try {
                        JsonObject json = gson.fromJson(response, JsonObject.class);
                        String token = json.get("token").getAsString();
                        Instant expiry = Instant.now().plus(TOKEN_CACHE_TTL);
                        installationTokenCache.put(installationId, new CachedToken(token, expiry));
                        log.info("Obtained installation access token for installation {} (cached until {})",
                                installationId, expiry);
                        return token;
                    } catch (Exception e) {
                        log.error("Failed to parse installation token response", e);
                        throw new RuntimeException("Failed to get installation token", e);
                    }
                })
                .retryWhen(WebClientConfig.buildRetrySpec("get-installation-token"))
                .doOnError(e -> log.error("Error fetching installation token for installation {} after retries",
                        installationId, e));
    }

    /**
     * Fetch unified diff for a PR using installation token auth.
     */
    public Mono<String> fetchDiff(String owner, String repo, int prNumber, long installationId) {
        String url = String.format("%s/repos/%s/%s/pulls/%d",
                gitHubProperties.getApiUrl(), owner, repo, prNumber);

        return getInstallationToken(installationId)
                .flatMap(token -> webClient.get()
                        .uri(url)
                        .header("Accept", "application/vnd.github.v3.diff")
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(String.class))
                .retryWhen(WebClientConfig.buildRetrySpec("fetch-diff"))
                .doOnError(e -> log.error("Error fetching diff for {}/{}/PR#{} after retries",
                        owner, repo, prNumber, e));
    }

    /**
     * Post a PR review with summary body and optional inline comments.
     */
    public Mono<Void> submitReview(String owner, String repo, int prNumber,
                                    String body, String event, List<ReviewComment> comments,
                                    long installationId) {
        String url = String.format("%s/repos/%s/%s/pulls/%d/reviews",
                gitHubProperties.getApiUrl(), owner, repo, prNumber);

        JsonObject reviewBody = new JsonObject();
        reviewBody.addProperty("body", body);
        reviewBody.addProperty("event", event);

        if (comments != null && !comments.isEmpty()) {
            JsonArray commentArray = new JsonArray();
            for (ReviewComment c : comments) {
                if (c.getPath() == null || c.getBody() == null) continue;
                JsonObject comment = new JsonObject();
                comment.addProperty("path", c.getPath());
                comment.addProperty("body", c.getBody());
                if (c.getLine() > 0) {
                    comment.addProperty("line", c.getLine());
                    comment.addProperty("side", c.getSide() != null ? c.getSide() : "RIGHT");
                }
                if (c.getStartLine() > 0 && c.getStartLine() != c.getLine()) {
                    comment.addProperty("start_line", c.getStartLine());
                }
                commentArray.add(comment);
            }
            reviewBody.add("comments", commentArray);
        }

        return getInstallationToken(installationId)
                .flatMap(token -> webClient.post()
                        .uri(url)
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github.v3+json")
                        .bodyValue(reviewBody.toString())
                        .retrieve()
                        .toBodilessEntity()
                        .then())
                .retryWhen(WebClientConfig.buildRetrySpec("submit-review"))
                .doOnError(e -> log.error("Error submitting review for {}/{}/PR#{} after retries",
                        owner, repo, prNumber, e));
    }

    /**
     * Close a pull request (state=closed). Does NOT merge.
     */
    public Mono<Void> closePullRequest(String owner, String repo, int prNumber, long installationId) {
        String url = String.format("%s/repos/%s/%s/pulls/%d",
                gitHubProperties.getApiUrl(), owner, repo, prNumber);

        JsonObject body = new JsonObject();
        body.addProperty("state", "closed");

        return getInstallationToken(installationId)
                .flatMap(token -> webClient.patch()
                        .uri(url)
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github.v3+json")
                        .bodyValue(body.toString())
                        .retrieve()
                        .toBodilessEntity()
                        .then())
                .retryWhen(WebClientConfig.buildRetrySpec("close-pr"))
                .doOnError(e -> log.error("Error closing PR {}/{}/PR#{} after retries",
                        owner, repo, prNumber, e));
    }

    /**
     * Post a regular issue comment on a PR.
     */
    public Mono<Void> postComment(String owner, String repo, int prNumber, String body, long installationId) {
        String url = String.format("%s/repos/%s/%s/issues/%d/comments",
                gitHubProperties.getApiUrl(), owner, repo, prNumber);

        JsonObject comment = new JsonObject();
        comment.addProperty("body", body);

        return getInstallationToken(installationId)
                .flatMap(token -> webClient.post()
                        .uri(url)
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github.v3+json")
                        .bodyValue(comment.toString())
                        .retrieve()
                        .toBodilessEntity()
                        .then())
                .retryWhen(WebClientConfig.buildRetrySpec("post-comment"))
                .doOnError(e -> log.error("Error posting comment for {}/{}/PR#{} after retries",
                        owner, repo, prNumber, e));
    }

    /**
     * Add labels to a PR (issues API).
     */
    public Mono<Void> addLabels(String owner, String repo, int prNumber, List<String> labels, long installationId) {
        String url = String.format("%s/repos/%s/%s/issues/%d/labels",
                gitHubProperties.getApiUrl(), owner, repo, prNumber);

        JsonArray labelArray = new JsonArray();
        for (String label : labels) {
            labelArray.add(label);
        }

        return getInstallationToken(installationId)
                .flatMap(token -> webClient.post()
                        .uri(url)
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github.v3+json")
                        .bodyValue(labelArray.toString())
                        .retrieve()
                        .toBodilessEntity()
                        .then())
                .retryWhen(WebClientConfig.buildRetrySpec("add-labels"))
                .doOnError(e -> log.error("Error adding labels for {}/{}/PR#{} after retries",
                        owner, repo, prNumber, e));
    }
}
