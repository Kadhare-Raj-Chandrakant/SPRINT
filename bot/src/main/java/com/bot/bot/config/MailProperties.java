package com.bot.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {
    private boolean enabled = false;
    private String host;
    private int port = 25;
    private String username;
    private String password;
    private String from;
    private String digestCron = "0 0 18 * * *";
    private List<String> maintainerEmails = new ArrayList<>();
}
