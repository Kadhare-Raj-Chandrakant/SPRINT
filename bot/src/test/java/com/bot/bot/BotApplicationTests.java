package com.bot.bot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "github.app-id=12345",
        "github.client-id=test-client-id",
        "github.webhook-secret=test-secret",
        "github.private-key-path=test-key.pem",
        "app.action-secret=test-action-secret",
        "llm.enabled=false"
})
@Testcontainers
class BotApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    @Test
    void contextLoads() {
    }

}
