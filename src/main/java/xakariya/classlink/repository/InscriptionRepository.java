package xakariya.classlink.repository;

import xakariya.classlink.domain.model.Inscription;

import java.util.List;
import java.util.Optional;

public interface InscriptionRepository {
    Inscription save(Inscription inscription);
    Optional<Inscription> findById(String id);
    Optional<Inscription> findByUtilisateurIdAndClasseId(String utilisateurId, String classeId);
    List<Inscription> findByUtilisateurId(String utilisateurId);
    List<Inscription> findByClasseId(String classeId);
    void deleteById(String id);
    void deleteByUtilisateurIdAndClasseId(String utilisateurId, String classeId);
}
