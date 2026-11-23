package xakariya.classlink.repository;

import xakariya.classlink.domain.enums.TypePublication;
import xakariya.classlink.domain.model.Publication;

import java.util.List;
import java.util.Optional;

public interface PublicationRepository {
    Publication save(Publication publication);
    Optional<Publication> findById(String id);
    List<Publication> findByClasseId(String classeId);
    List<Publication> findByClasseIdAndType(String classeId, TypePublication type);
    void deleteById(String id);
}
