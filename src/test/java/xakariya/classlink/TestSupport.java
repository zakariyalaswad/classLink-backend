package xakariya.classlink;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import xakariya.classlink.repository.DocumentStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Test doubles: in-memory document store + dummy JWT decoder (tokens are injected by MockMvc). */
@TestConfiguration
@Profile("test")
public class TestSupport {

    @Bean
    public DocumentStore documentStore() {
        return new DocumentStore() {
            private final Map<String, Map<String, Map<String, Object>>> db = new ConcurrentHashMap<>();

            private Map<String, Map<String, Object>> col(String c) {
                return db.computeIfAbsent(c, k -> new ConcurrentHashMap<>());
            }

            @Override
            public void set(String c, String id, Map<String, Object> data) {
                col(c).put(id, new HashMap<>(data));
            }

            @Override
            public Optional<Map<String, Object>> get(String c, String id) {
                return Optional.ofNullable(col(c).get(id)).map(HashMap::new);
            }

            @Override
            public List<Map<String, Object>> find(String c, Map<String, Object> filter) {
                return col(c).values().stream()
                        .filter(d -> filter.entrySet().stream().allMatch(e -> Objects.equals(d.get(e.getKey()), e.getValue())))
                        .map(d -> (Map<String, Object>) new HashMap<>(d)).toList();
            }

            @Override
            public void delete(String c, String id) {
                col(c).remove(id);
            }
        };
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); };
    }
}
