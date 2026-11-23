package xakariya.classlink.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minimal document-database port. Production: Firestore. Tests: in-memory. */
public interface DocumentStore {

    void set(String collection, String id, Map<String, Object> data);

    Optional<Map<String, Object>> get(String collection, String id);

    /** Documents whose fields equal all given values (empty filter = whole collection). */
    List<Map<String, Object>> find(String collection, Map<String, Object> equalsFilter);

    void delete(String collection, String id);
}
