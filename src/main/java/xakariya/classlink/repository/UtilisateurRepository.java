package xakariya.classlink.repository;

import xakariya.classlink.domain.model.Utilisateur;

import java.util.Optional;

public interface UtilisateurRepository {
    Utilisateur save(Utilisateur utilisateur);
    Optional<Utilisateur> findById(String id);
    void deleteById(String id);
}
