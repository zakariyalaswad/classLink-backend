package xakariya.classlink.repository;

import org.springframework.stereotype.Repository;
import xakariya.classlink.domain.model.Utilisateur;

@Repository
public class UtilisateurRepositoryImpl extends AbstractRepository<Utilisateur> implements UtilisateurRepository {

    public UtilisateurRepositoryImpl(DocumentStore store) {
        super(store, "utilisateurs", Utilisateur.class, Utilisateur::getId, Utilisateur::setId);
    }
}
