package xakariya.classlink.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Firebase Realtime Database through its REST API, authenticated with the database secret. */
@Component
@Profile("!test")
public class RealtimeDatabaseStore implements DocumentStore {

    private final String baseUrl;
    private final String secret;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public RealtimeDatabaseStore(@Value("${firebase.database-url}") String databaseUrl,
                                 @Value("${firebase.database-secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("firebase.database-secret is not set (see README).");
        }
        this.baseUrl = databaseUrl.replaceAll("/+$", "");
        this.secret = secret;
    }

    @Override
    public void set(String collection, String id, Map<String, Object> data) {
        send("PUT", collection + "/" + id, data);
    }

    @Override
    public Optional<Map<String, Object>> get(String collection, String id) {
        Map<String, Object> m = read(collection + "/" + id, new TypeReference<>() {});
        return Optional.ofNullable(m);
    }

    @Override
    public List<Map<String, Object>> find(String collection, Map<String, Object> filter) {
        Map<String, Map<String, Object>> all = read(collection, new TypeReference<>() {});
        if (all == null) return List.of();
        return all.values().stream()
                .filter(d -> filter.entrySet().stream().allMatch(e -> Objects.equals(
                        String.valueOf(d.get(e.getKey())), String.valueOf(e.getValue()))))
                .toList();
    }

    @Override
    public void delete(String collection, String id) {
        send("DELETE", collection + "/" + id, null);
    }

    private URI uri(String path) {
        return URI.create(baseUrl + "/" + path + ".json?auth=" + URLEncoder.encode(secret, StandardCharsets.UTF_8));
    }

    private <T> T read(String path, TypeReference<T> type) {
        String body = send("GET", path, null);
        try {
            return mapper.readValue(body, type); // "null" -> null
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable Realtime Database response", e);
        }
    }

    private String send(String method, String path, Object payload) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(uri(path));
            if (payload == null) b.method(method, HttpRequest.BodyPublishers.noBody());
            else b.method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .header("Content-Type", "application/json");
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() >= 300) {
                throw new IllegalStateException("Realtime Database error " + r.statusCode() + ": " + r.body());
            }
            return r.body();
        } catch (IOException e) {
            throw new IllegalStateException("Realtime Database unreachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }
}