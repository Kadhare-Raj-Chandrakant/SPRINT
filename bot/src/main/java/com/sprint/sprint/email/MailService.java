package com.sprint.sprint.email;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sprint.sprint.config.MailProperties;

import jakarta.mail.internet.MimeMessage;

import java.util.List;

/**
 * Thin wrapper over JavaMailSender that no-ops (logs) when mail is disabled
 * or no sender bean is present, so callers never need null checks.
 */
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final MailProperties props;
    private final JavaMailSender sender;

    public MailService(MailProperties props, JavaMailSender sender) {
        this.props = props;
        this.sender = sender;
    }

    public boolean isEnabled() {
        return props.isEnabled() && sender != null;
    }

    public void sendEmail(List<String> to, String subject, String htmlBody) {
        if (!isEnabled()) {
            log.info("Mail disabled - skipping send: subject='{}'", subject);
            return;
        }
        if (to == null || to.isEmpty()) {
            log.warn("Mail enabled but no recipients for subject='{}' - skipping", subject);
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(props.getFrom());
            helper.setTo(to.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            sender.send(message);
            log.info("Sent email '{}' to {} recipient(s)", subject, to.size());
        } catch (Exception e) {
            log.error("Failed to send email '{}': {}", subject, e.getMessage(), e);
        }
    }
}
