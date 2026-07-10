package com.bot.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Main application class for the PR Review Bot.
 * Bootstraps Spring Boot and loads .env configuration before context initialization.
 */
@SpringBootApplication
public class BotApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(BotApplication.class, args);
    }

    /**
     * Loads .env file from the working directory into system properties.
     * Spring's property resolution ({@code ${...}}) checks system properties
     * after OS environment variables, so values set here will be available
     * for {@code application.yaml} placeholder resolution.
     *
     * Does NOT override existing OS environment variables.
     */
    private static void loadDotEnv() {
        File envFile = findEnvFile();
        if (envFile == null) {
            System.err.println("Warning: .env file not found (checked ./, ./bot/.env, ../.env)");
            return;
        }

        try {
            Properties props = new Properties();
            try (FileReader reader = new FileReader(envFile, StandardCharsets.UTF_8)) {
                props.load(reader);
            }

            int loaded = 0;
            for (String key : props.stringPropertyNames()) {
                if (System.getenv(key) == null && System.getProperty(key) == null) {
                    System.setProperty(key, props.getProperty(key));
                    loaded++;
                }
            }
            System.out.println("Loaded " + loaded + " entries from " + envFile.getName());
        } catch (IOException e) {
            System.err.println("Warning: Failed to load .env file: " + e.getMessage());
        }
    }

    /** Search common locations for the .env file. */
    private static File findEnvFile() {
        String[] candidates = { ".env", "bot/.env", "../.env" };
        for (String path : candidates) {
            File f = new File(path);
            if (f.isFile()) return f;
        }
        return null;
    }
}
