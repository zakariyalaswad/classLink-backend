package xakariya.classlink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Delivers ClassLink transactional notification emails over HTTPS (port 443) via the Resend Email API.
 * Never exposes API keys or authorization headers in logs or persisted error messages.
 */
@Service
public class ResendEmailSender {

    public static final String DEFAULT_RESEND_API_URL = "https://api.resend.com/emails";

    private static final Pattern SIMPLE_EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    private static final Pattern DISPLAY_NAME_EMAIL_PATTERN = Pattern.compile(
            "^.+<([A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})>$"
    );
    private static final Pattern RESEND_KEY_PATTERN = Pattern.compile("re_[A-Za-z0-9_]{6,}");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)Bearer\\s+[^\\s,;\"']+");

    /** Pluggable HTTP transport for clean unit/mock testing without network sockets when desired. */
    @FunctionalInterface
    public interface HttpTransport {
        HttpExchangeResult execute(URI uri, Map<String, String> headers, String jsonBody)
                throws IOException, InterruptedException;
    }

    public record HttpExchangeResult(int statusCode, String body) {}

    public static class ResendDeliveryException extends RuntimeException {
        private final int statusCode;
        private final boolean retryable;

        public ResendDeliveryException(String message, int statusCode, boolean retryable) {
            super(message);
            this.statusCode = statusCode;
            this.retryable = retryable;
        }

        public ResendDeliveryException(String message, int statusCode, boolean retryable, Throwable cause) {
            super(message, cause);
            this.statusCode = statusCode;
            this.retryable = retryable;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }

    private final String apiKey;
    private final String mailFrom;
    private final URI apiUri;
    private final HttpTransport transport;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public ResendEmailSender(@Value("${resend.api-key:}") String apiKey,
                             @Value("${app.mail.from:}") String mailFrom,
                             @Value("${resend.api-url:" + DEFAULT_RESEND_API_URL + "}") String apiUrl) {
        this(apiKey, mailFrom, apiUrl, buildDefaultHttpTransport());
    }

    public ResendEmailSender(String apiKey,
                             String mailFrom,
                             String apiUrl,
                             HttpTransport transport) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.mailFrom = mailFrom != null ? mailFrom.trim() : "";
        String resolvedUrl = (apiUrl != null && !apiUrl.isBlank()) ? apiUrl.trim() : DEFAULT_RESEND_API_URL;
        this.apiUri = validateAndParseUri(resolvedUrl);
        this.transport = transport != null ? transport : buildDefaultHttpTransport();
    }

    private static HttpTransport buildDefaultHttpTransport() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return (uri, headers, jsonBody) -> {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
            headers.forEach(builder::header);
            HttpResponse<String> response = client.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            return new HttpExchangeResult(response.statusCode(), response.body());
        };
    }

    private static URI validateAndParseUri(String rawUrl) {
        URI uri = URI.create(rawUrl);
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
        boolean isLoopback = "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host) || "[::1]".equals(host);
        if (!"https".equals(scheme) && !("http".equals(scheme) && isLoopback)) {
            throw new IllegalArgumentException("Resend API URL must use HTTPS (port 443): " + rawUrl);
        }
        return uri;
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty() && !mailFrom.isEmpty();
    }

    public boolean isValidSenderAddress(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        String trimmed = candidate.trim();
        if (trimmed.contains("..")) {
            return false;
        }
        if (SIMPLE_EMAIL_PATTERN.matcher(trimmed).matches()) {
            return true;
        }
        Matcher m = DISPLAY_NAME_EMAIL_PATTERN.matcher(trimmed);
        return m.matches() && !m.group(1).contains("..");
    }

    public String getEffectiveFromHeader() {
        if (mailFrom.isEmpty()) {
            throw new ResendDeliveryException("Configuration Resend incomplète (MAIL_FROM manquant)", 0, false);
        }
        if (!isValidSenderAddress(mailFrom)) {
            throw new ResendDeliveryException("Adresse d'expéditeur MAIL_FROM invalide : " + mailFrom, 0, false);
        }
        if (mailFrom.contains("<") && mailFrom.endsWith(">")) {
            return mailFrom;
        }
        return "ClassLink <" + mailFrom + ">";
    }

    /**
     * Sends a single multipart (HTML + plain-text) email through the Resend HTTPS API.
     *
     * @return the Resend email ID returned by the API (e.g. {@code re_...})
     */
    public String sendHtmlEmail(String toEmail, String subject, String htmlBody, String plainTextBody) {
        if (apiKey.isEmpty()) {
            throw new ResendDeliveryException("Resend API non configurée (RESEND_API_KEY manquante)", 0, false);
        }
        String fromHeader = getEffectiveFromHeader();
        if (toEmail == null || toEmail.isBlank()) {
            throw new ResendDeliveryException("Adresse email destinataire manquante", 0, false);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", fromHeader);
        payload.put("to", List.of(toEmail.trim()));
        payload.put("subject", subject != null ? subject : "");
        payload.put("html", htmlBody != null ? htmlBody : "");
        if (plainTextBody != null && !plainTextBody.isBlank()) {
            payload.put("text", plainTextBody);
        }

        final String jsonBody;
        try {
            jsonBody = mapper.writeValueAsString(payload);
        } catch (IOException e) {
            throw new ResendDeliveryException("Impossible de sérialiser la requête Resend", 0, false, e);
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "ClassLink-Backend/1.0");

        final HttpExchangeResult response;
        try {
            response = transport.execute(apiUri, headers, jsonBody);
        } catch (HttpTimeoutException e) {
            throw new ResendDeliveryException(
                    "Délai d'attente dépassé vers Resend API : " + sanitizeErrorMessage(e), 0, true, e);
        } catch (IOException e) {
            throw new ResendDeliveryException(
                    "Erreur réseau vers Resend API : " + sanitizeErrorMessage(e), 0, true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResendDeliveryException("Envoi email Resend interrompu", 0, false, e);
        }

        int status = response.statusCode();
        String body = response.body() != null ? response.body() : "";

        if (status >= 200 && status < 300) {
            return extractMessageId(body);
        }

        String apiErrorDetail = sanitizeString(extractErrorDetail(body, status));

        if (status == 401 || status == 403) {
            throw new ResendDeliveryException(
                    "Authentification ou autorisation Resend refusée (HTTP " + status + ") : " + apiErrorDetail,
                    status,
                    false
            );
        }
        if (status == 400 || status == 422) {
            throw new ResendDeliveryException(
                    "Requête ou expéditeur invalide Resend (HTTP " + status + ") : " + apiErrorDetail,
                    status,
                    false
            );
        }
        if (status == 429) {
            throw new ResendDeliveryException(
                    "Limite de débit Resend atteinte (HTTP 429) : " + apiErrorDetail,
                    429,
                    true
            );
        }
        if (status >= 500) {
            throw new ResendDeliveryException(
                    "Erreur serveur Resend (HTTP " + status + ") : " + apiErrorDetail,
                    status,
                    true
            );
        }
        throw new ResendDeliveryException(
                "Erreur Resend API (HTTP " + status + ") : " + apiErrorDetail,
                status,
                false
        );
    }

    private String extractMessageId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "accepted";
        }
        try {
            JsonNode node = mapper.readTree(responseBody);
            if (node.hasNonNull("id")) {
                return node.get("id").asText();
            }
        } catch (Exception ignored) {
        }
        return "accepted";
    }

    private String extractErrorDetail(String responseBody, int status) {
        if (responseBody == null || responseBody.isBlank()) {
            return "HTTP " + status;
        }
        try {
            JsonNode node = mapper.readTree(responseBody);
            String name = node.hasNonNull("name") ? node.get("name").asText().trim() : "";
            String message = node.hasNonNull("message") ? node.get("message").asText().trim() : "";
            if (!name.isEmpty() && !message.isEmpty()) {
                return name + " - " + message;
            }
            if (!message.isEmpty()) {
                return message;
            }
            if (!name.isEmpty()) {
                return name;
            }
        } catch (Exception ignored) {
        }
        return responseBody.trim();
    }

    public String sanitizeErrorMessage(Throwable error) {
        if (error == null) {
            return "Erreur Resend inconnue";
        }
        String msg = error.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = error.getClass().getSimpleName();
        }
        return sanitizeString(msg);
    }

    private String sanitizeString(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Erreur Resend inconnue";
        }
        String sanitized = raw;
        if (!apiKey.isEmpty()) {
            sanitized = sanitized.replace(apiKey, "***");
        }
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("Bearer ***");
        sanitized = RESEND_KEY_PATTERN.matcher(sanitized).replaceAll("re_***");
        return sanitized.length() > 300 ? sanitized.substring(0, 300) : sanitized;
    }

    @Override
    public String toString() {
        return "ResendEmailSender{apiUri=" + apiUri + ", mailFrom='" + mailFrom + "', configured=" + isConfigured() + "}";
    }
}
