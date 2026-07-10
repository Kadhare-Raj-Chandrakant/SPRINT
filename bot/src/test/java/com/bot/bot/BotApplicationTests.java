package com.bot.bot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "github.app-id=12345",
    "github.client-id=test-client-id",
    "github.webhook-secret=test-secret",
    "github.private-key-path=test-key.pem"
})
class BotApplicationTests {

    @Test
    void contextLoads() {
    }

}
