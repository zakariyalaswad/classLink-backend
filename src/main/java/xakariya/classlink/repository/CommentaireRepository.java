package xakariya.classlink.repository;

import xakariya.classlink.domain.model.Commentaire;

import java.util.List;
import java.util.Optional;

public interface CommentaireRepository {
    Commentaire save(Commentaire commentaire);
    Optional<Commentaire> findById(String id);
    List<Commentaire> findByPublicationId(String publicationId);
    void deleteById(String id);
}
