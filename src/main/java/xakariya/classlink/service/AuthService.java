package xakariya.classlink.service;

import org.springframework.stereotype.Service;
import xakariya.classlink.domain.model.Utilisateur;
import xakariya.classlink.repository.UtilisateurRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Authentication itself (email/password, tokens) is delegated to Firebase Authentication.
 * This service only manages the application-side user profile, keyed by the Firebase uid.
 */
@Service
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;

    public AuthService(UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    /** Creates the profile of a freshly registered Firebase user (idempotent). */
    public Utilisateur inscrire(String uid, String nom, String prenom, String email) {
        if (nom == null || nom.isBlank() || prenom == null || prenom.isBlank()) {
            throw new IllegalArgumentException("Le nom et le prénom sont requis");
        }
        Optional<Utilisateur> existing = utilisateurRepository.findById(uid);
        if (existing.isPresent()) {
            return existing.get();
        }
        Utilisateur u = new Utilisateur();
        u.setId(uid);
        u.setNom(nom.trim());
        u.setPrenom(prenom.trim());
        u.setEmail(email == null ? "" : email.trim().toLowerCase());
        u.setDateCreation(LocalDateTime.now());
        return utilisateurRepository.save(u);
    }

    public Optional<Utilisateur> getUtilisateurCourant(String id) {
        return utilisateurRepository.findById(id);
    }
}
