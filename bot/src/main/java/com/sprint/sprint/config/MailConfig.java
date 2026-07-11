package com.sprint.sprint.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Properties;

import com.sprint.sprint.email.MailService;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
    public JavaMailSender javaMailSender(MailProperties props) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(props.getHost());
        sender.setPort(props.getPort());
        sender.setUsername(props.getUsername());
        sender.setPassword(props.getPassword());
        sender.setDefaultEncoding("UTF-8");
        Properties javaMail = new Properties();
        javaMail.put("mail.transport.protocol", "smtp");
        javaMail.put("mail.smtp.auth", props.getUsername() != null && !props.getUsername().isBlank());
        javaMail.put("mail.smtp.starttls.enable", "true");
        // ponytail: Gmail :587 requires STARTTLS. If switching to :465, also add mail.smtp.socketFactory.port=465.
        sender.setJavaMailProperties(javaMail);
        return sender;
    }

    @Bean
    public MailService mailService(MailProperties props, ObjectProvider<JavaMailSender> sender) {
        return new MailService(props, sender.getIfAvailable());
    }
}
