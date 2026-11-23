package xakariya.classlink.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Generic entity <-> document mapping (java.time as ISO strings, enums by name). */
abstract class AbstractRepository<T> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);

    private final DocumentStore store;
    private final String collection;
    private final Class<T> type;
    private final Function<T, String> idGetter;
    private final BiConsumer<T, String> idSetter;

    AbstractRepository(DocumentStore store, String collection, Class<T> type,
                       Function<T, String> idGetter, BiConsumer<T, String> idSetter) {
        this.store = store;
        this.collection = collection;
        this.type = type;
        this.idGetter = idGetter;
        this.idSetter = idSetter;
    }

    public T save(T entity) {
        String id = idGetter.apply(entity);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
            idSetter.accept(entity, id);
        }
        store.set(collection, id, MAPPER.convertValue(entity, new TypeReference<Map<String, Object>>() {}));
        return entity;
    }

    public Optional<T> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return store.get(collection, id).map(this::toEntity);
    }

    public void deleteById(String id) {
        if (id != null && !id.isBlank()) store.delete(collection, id);
    }

    /** Equality query: where("a", 1, "b", 2). */
    protected List<T> where(Object... fieldValuePairs) {
        Map<String, Object> filter = new LinkedHashMap<>();
        for (int i = 0; i < fieldValuePairs.length; i += 2) {
            filter.put((String) fieldValuePairs[i], fieldValuePairs[i + 1]);
        }
        return store.find(collection, filter).stream().map(this::toEntity).toList();
    }

    private T toEntity(Map<String, Object> data) {
        return MAPPER.convertValue(data, type);
    }
}
