package xakariya.classlink.repository;

import org.springframework.stereotype.Repository;
import xakariya.classlink.domain.model.Commentaire;

import java.util.List;

@Repository
public class CommentaireRepositoryImpl extends AbstractRepository<Commentaire> implements CommentaireRepository {

    public CommentaireRepositoryImpl(DocumentStore store) {
        super(store, "commentaires", Commentaire.class, Commentaire::getId, Commentaire::setId);
    }

    @Override
    public List<Commentaire> findByPublicationId(String publicationId) {
        return where("publicationId", publicationId);
    }
}
