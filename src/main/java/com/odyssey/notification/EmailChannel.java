package com.odyssey.notification;

import com.odyssey.model.NotificationMessage;
import com.odyssey.util.NotificationMasker;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;

@Slf4j
@Component
public class EmailChannel implements NotificationChannel {

    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUsername;
    private final String smtpPassword;
    private final String mailFrom;

    private JavaMailSender customMailSender;

    public EmailChannel(
            @Value("${SMTP_HOST:#{null}}") String smtpHost,
            @Value("${SMTP_PORT:587}") int smtpPort,
            @Value("${SMTP_USERNAME:#{null}}") String smtpUsername,
            @Value("${SMTP_PASSWORD:#{null}}") String smtpPassword,
            @Value("${MAIL_FROM:#{null}}") String mailFrom) {
        this.smtpHost = smtpHost != null && !smtpHost.isBlank() ? smtpHost.trim() : null;
        this.smtpPort = smtpPort > 0 ? smtpPort : 587;
        this.smtpUsername = smtpUsername != null && !smtpUsername.isBlank() ? smtpUsername.trim() : null;
        this.smtpPassword = smtpPassword != null ? smtpPassword : "";
        this.mailFrom = mailFrom != null && !mailFrom.isBlank() ? mailFrom.trim() : null;
    }

    public void setMailSenderForTesting(JavaMailSender mailSender) {
        this.customMailSender = mailSender;
    }

    @Override
    public String getChannelType() {
        return "EMAIL";
    }

    @Override
    public boolean isActive() {
        return (smtpHost != null && smtpUsername != null) || customMailSender != null;
    }

    @Override
    public SendResult send(NotificationMessage message) {
        String recipient = message.getRecipient();
        String maskedRecipient = NotificationMasker.maskEmail(recipient);

        if (!NotificationMasker.isValidEmail(recipient)) {
            log.info("EmailChannel: Recipient {} is missing or invalid - skipping", maskedRecipient);
            return SendResult.builder()
                    .status(SendStatus.SKIPPED)
                    .error("Missing or invalid email recipient: " + maskedRecipient)
                    .sentAt(LocalDateTime.now())
                    .build();
        }

        if (!isActive()) {
            log.debug("EmailChannel: Inactive (SMTP credentials not set). Alert recorded as SIMULATED for {}", maskedRecipient);
            return SendResult.builder()
                    .status(SendStatus.SIMULATED)
                    .error("SMTP not configured - simulated alert")
                    .sentAt(LocalDateTime.now())
                    .build();
        }

        JavaMailSender mailSender = customMailSender != null ? customMailSender : createMailSender();
        String fromAddress = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom : smtpUsername;
        String providerMessageId = "EMAIL-" + UUID.randomUUID();

        Exception lastException = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
                helper.setFrom(new InternetAddress(fromAddress, "Odyssey Travel"));
                helper.setTo(recipient.trim());
                helper.setSubject(message.getSubject() != null ? message.getSubject() : "Odyssey Travel Notification");
                helper.setText(message.getMessage() != null ? message.getMessage() : "", false);

                if (message.getReplyTo() != null && NotificationMasker.isValidEmail(message.getReplyTo())) {
                    helper.setReplyTo(message.getReplyTo().trim());
                }

                mailSender.send(mimeMessage);
                log.info("EmailChannel: Successfully sent email to recipient: {}", maskedRecipient);

                return SendResult.builder()
                        .status(SendStatus.SENT)
                        .providerMessageId(providerMessageId)
                        .sentAt(LocalDateTime.now())
                        .build();
            } catch (Exception e) {
                lastException = e;
                log.warn("EmailChannel: Attempt {} failed to send email to {}: {}", attempt, maskedRecipient, e.getMessage());
                if (attempt == 1) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("EmailChannel: Failed to deliver email to {} after retries: {}", maskedRecipient,
                lastException != null ? lastException.getMessage() : "Unknown error");

        return SendResult.builder()
                .status(SendStatus.FAILED)
                .error(lastException != null ? lastException.getMessage() : "Failed to send email")
                .sentAt(LocalDateTime.now())
                .build();
    }

    private JavaMailSender createMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtpHost);
        sender.setPort(smtpPort);
        sender.setUsername(smtpUsername);
        sender.setPassword(smtpPassword);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        return sender;
    }
}
