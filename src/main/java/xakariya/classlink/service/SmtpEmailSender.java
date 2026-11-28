package xakariya.classlink.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

/**
 * Handles low-level SMTP email delivery using Spring Boot's JavaMailSender (Jakarta Mail).
 * Never exposes SMTP passwords or credentials in error messages or logs.
 */
@Service
public class SmtpEmailSender {

    private final JavaMailSender mailSender;
    private final String smtpHost;
    private final int smtpPort;
    private final String mailUsername;
    private final String mailPassword;
    private final String mailFrom;

    @Autowired
    public SmtpEmailSender(Optional<JavaMailSender> injectedSender,
                           @Value("${spring.mail.host:smtp.gmail.com}") String smtpHost,
                           @Value("${spring.mail.port:587}") int smtpPort,
                           @Value("${spring.mail.username:}") String mailUsername,
                           @Value("${spring.mail.password:}") String mailPassword,
                           @Value("${app.mail.from:}") String mailFrom) {
        this.smtpHost = smtpHost != null ? smtpHost.trim() : "smtp.gmail.com";
        this.smtpPort = smtpPort;
        this.mailUsername = mailUsername != null ? mailUsername.trim() : "";
        this.mailPassword = mailPassword != null ? mailPassword.trim().replace(" ", "") : "";
        this.mailFrom = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom.trim() : this.mailUsername;
        this.mailSender = injectedSender.orElseGet(this::buildDefaultSender);
    }

    private JavaMailSender buildDefaultSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(this.smtpHost);
        sender.setPort(this.smtpPort);
        sender.setUsername(this.mailUsername);
        sender.setPassword(this.mailPassword);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(!this.mailUsername.isEmpty()));
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "6000");
        props.put("mail.smtp.timeout", "6000");
        props.put("mail.smtp.writetimeout", "6000");
        return sender;
    }

    public boolean isConfigured() {
        if (mailSender instanceof JavaMailSenderImpl impl) {
            String host = impl.getHost();
            // In local/test SMTP servers without auth (e.g. GreenMail / localhost test server), host + port is enough;
            // For public SMTP relays (e.g. smtp.gmail.com), username and password are required.
            if (host == null || host.isBlank()) {
                return false;
            }
            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
                return impl.getPort() > 0;
            }
            return !mailUsername.isEmpty() && !mailPassword.isEmpty();
        }
        return mailSender != null;
    }

    public String getEffectiveFromAddress() {
        if (mailFrom != null && !mailFrom.isBlank()) {
            return mailFrom;
        }
        if (mailUsername != null && !mailUsername.isBlank()) {
            return mailUsername;
        }
        return "no-reply@classlink.local";
    }

    public void sendHtmlEmail(String toEmail, String subject, String htmlBody, String plainTextBody) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("SMTP non configuré (MAIL_USERNAME / MAIL_PASSWORD manquants)");
        }
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                StandardCharsets.UTF_8.name()
        );
        helper.setFrom(new InternetAddress(getEffectiveFromAddress(), "ClassLink", StandardCharsets.UTF_8.name()));
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(plainTextBody != null ? plainTextBody : "", htmlBody);

        mailSender.send(mimeMessage);
    }

    public String sanitizeErrorMessage(Throwable error) {
        if (error == null) return "Erreur SMTP inconnue";
        String msg = error.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = error.getClass().getSimpleName();
        }
        if (!mailPassword.isEmpty()) {
            msg = msg.replace(mailPassword, "***");
        }
        return msg.length() > 300 ? msg.substring(0, 300) : msg;
    }
}
