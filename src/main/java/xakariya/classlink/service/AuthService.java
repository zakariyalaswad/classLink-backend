package xakariya.classlink.service;

import org.springframework.stereotype.Service;
import xakariya.classlink.domain.enums.TypeNotification;
import xakariya.classlink.domain.model.PreferenceNotification;
import xakariya.classlink.domain.model.Utilisateur;
import xakariya.classlink.repository.PreferenceNotificationRepository;
import xakariya.classlink.repository.UtilisateurRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Authentication itself (email/password, tokens) is delegated to Firebase Authentication.
 * This service manages the application-side user profile, keyed by the Firebase uid.
 */
@Service
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PreferenceNotificationRepository preferenceRepository;

    public AuthService(UtilisateurRepository utilisateurRepository,
                       PreferenceNotificationRepository preferenceRepository) {
        this.utilisateurRepository = utilisateurRepository;
        this.preferenceRepository = preferenceRepository;
    }

    /** Creates the profile and database notification preferences of a registered Firebase user (idempotent). */
    public Utilisateur inscrire(String uid, String nom, String prenom, String email) {
        if (nom == null || nom.isBlank() || prenom == null || prenom.isBlank()) {
            throw new IllegalArgumentException("Le nom et le prénom sont requis");
        }
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        Optional<Utilisateur> existing = utilisateurRepository.findById(uid);
        if (existing.isPresent()) {
            Utilisateur found = existing.get();
            if ((found.getEmail() == null || found.getEmail().isBlank()) && !normalizedEmail.isEmpty()) {
                found.setEmail(normalizedEmail);
                found = utilisateurRepository.save(found);
            }
            initPreferencesInDatabaseIfEmpty(uid);
            return found;
        }
        Utilisateur u = new Utilisateur();
        u.setId(uid);
        u.setNom(nom.trim());
        u.setPrenom(prenom.trim());
        u.setEmail(normalizedEmail);
        u.setDateCreation(LocalDateTime.now());
        Utilisateur saved = utilisateurRepository.save(u);
        initPreferencesInDatabaseIfEmpty(uid);
        return saved;
    }

    private void initPreferencesInDatabaseIfEmpty(String utilisateurId) {
        if (preferenceRepository.findByUtilisateurId(utilisateurId).isEmpty()) {
            for (TypeNotification type : TypeNotification.values()) {
                PreferenceNotification pref = new PreferenceNotification();
                pref.setUtilisateurId(utilisateurId);
                pref.setType(type);
                preferenceRepository.save(pref);
            }
        }
    }

    public Optional<Utilisateur> getUtilisateurCourant(String id) {
        return getUtilisateurCourant(id, null);
    }

    public Optional<Utilisateur> getUtilisateurCourant(String id, String tokenEmail) {
        Optional<Utilisateur> opt = utilisateurRepository.findById(id);
        if (opt.isPresent() && tokenEmail != null && !tokenEmail.isBlank()) {
            Utilisateur u = opt.get();
            if (u.getEmail() == null || u.getEmail().isBlank()) {
                u.setEmail(tokenEmail.trim().toLowerCase());
                return Optional.of(utilisateurRepository.save(u));
            }
        }
        return opt;
    }
}
