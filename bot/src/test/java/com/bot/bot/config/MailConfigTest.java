package com.bot.bot.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import com.bot.bot.email.MailService;

import static org.assertj.core.api.Assertions.assertThat;

class MailConfigTest {

    private final ApplicationContextRunner enabledRunner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(MailConfig.class))
            .withPropertyValues(
                    "app.mail.enabled=true",
                    "app.mail.host=smtp.example.com",
                    "app.mail.port=587",
                    "app.mail.username=user",
                    "app.mail.password=pass");

    private final ApplicationContextRunner disabledRunner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(MailConfig.class))
            .withPropertyValues("app.mail.enabled=false");

    @Test
    void senderPresentWhenEnabled() {
        enabledRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(JavaMailSender.class);
            assertThat(ctx.getBean(MailService.class).isEnabled()).isTrue();
        });
    }

    @Test
    void noOpWhenDisabled() {
        disabledRunner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(JavaMailSender.class);
            assertThat(ctx.getBean(MailService.class).isEnabled()).isFalse();
        });
    }
}
