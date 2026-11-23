package xakariya.classlink.repository;

import org.springframework.stereotype.Repository;
import xakariya.classlink.domain.model.Inscription;

import java.util.List;
import java.util.Optional;

@Repository
public class InscriptionRepositoryImpl extends AbstractRepository<Inscription> implements InscriptionRepository {

    public InscriptionRepositoryImpl(DocumentStore store) {
        super(store, "inscriptions", Inscription.class, Inscription::getId, Inscription::setId);
    }

    @Override
    public Optional<Inscription> findByUtilisateurIdAndClasseId(String utilisateurId, String classeId) {
        return where("utilisateurId", utilisateurId, "classeId", classeId).stream().findFirst();
    }

    @Override
    public List<Inscription> findByUtilisateurId(String utilisateurId) {
        return where("utilisateurId", utilisateurId);
    }

    @Override
    public List<Inscription> findByClasseId(String classeId) {
        return where("classeId", classeId);
    }

    @Override
    public void deleteByUtilisateurIdAndClasseId(String utilisateurId, String classeId) {
        findByUtilisateurIdAndClasseId(utilisateurId, classeId).ifPresent(i -> deleteById(i.getId()));
    }
}
