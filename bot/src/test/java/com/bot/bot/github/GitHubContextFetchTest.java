package com.bot.bot.github;

import com.bot.bot.config.GitHubProperties;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises fetchFile / fetchLinkedIssues against a JDK-built fake GitHub
 * server (no Testcontainers/network needed). The JWT generator is stubbed.
 */
class GitHubContextFetchTest {

    private HttpServer server;
    private GitHubApiClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        String readme = Base64.getEncoder()
                .encodeToString("README context text".getBytes(StandardCharsets.UTF_8));

        respond("/app/installations/123/access_tokens", "{\"token\":\"fake-token\"}");
        respond("/repos/o/r/contents/README.md",
                "{\"content\":\"" + readme + "\",\"encoding\":\"base64\",\"size\":100}");
        respond("/repos/o/r/pulls/5", "{\"body\":\"fixes #7 and closes #8\"}");
        respond("/repos/o/r/issues/7", "{\"body\":\"issue seven body\"}");
        respond("/repos/o/r/issues/8", "{\"body\":\"issue eight body\"}");
        server.start();

        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        GitHubProperties props = new GitHubProperties();
        props.setApiUrl(baseUrl);
        GitHubJwtGenerator jwt = new GitHubJwtGenerator(new GitHubProperties()) {
            @Override
            public String generateAppToken() {
                return "dummy.jwt.token";
            }
        };
        client = new GitHubApiClient(props, jwt, WebClient.create(baseUrl), new Gson());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchFile_decodesBase64Content() {
        String content = client.fetchFile("o", "r", "README.md", 123).block();
        assertThat(content).isEqualTo("README context text");
    }

    @Test
    void fetchLinkedIssues_collectsReferencedIssueBodies() {
        String issues = client.fetchLinkedIssues("o", "r", 5, 123).block();
        assertThat(issues).contains("issue seven body").contains("issue eight body");
    }

    private void respond(String path, String body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }
}
