package xakariya.classlink.repository;

import org.springframework.stereotype.Repository;
import xakariya.classlink.domain.enums.TypePublication;
import xakariya.classlink.domain.model.Publication;

import java.util.List;

@Repository
public class PublicationRepositoryImpl extends AbstractRepository<Publication> implements PublicationRepository {

    public PublicationRepositoryImpl(DocumentStore store) {
        super(store, "publications", Publication.class, Publication::getId, Publication::setId);
    }

    @Override
    public List<Publication> findByClasseId(String classeId) {
        return where("classeId", classeId);
    }

    @Override
    public List<Publication> findByClasseIdAndType(String classeId, TypePublication type) {
        return where("classeId", classeId, "type", type.name());
    }
}
