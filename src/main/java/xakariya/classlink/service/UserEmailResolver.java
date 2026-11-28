package xakariya.classlink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xakariya.classlink.domain.model.Inscription;
import xakariya.classlink.domain.model.Utilisateur;
import xakariya.classlink.repository.InscriptionRepository;
import xakariya.classlink.repository.UtilisateurRepository;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Resolves and validates a notification recipient's email address from their Firebase UID.
 * Hierarchy:
 * 1. Utilisateur record in Firebase Realtime Database (utilisateurs/{uid}.email)
 * 2. Denormalized Inscription record in Firebase Realtime Database (inscriptions.emailUtilisateur)
 * 3. External Firebase Authentication UID lookup provider (when configured)
 */
@Service
public class UserEmailResolver {

    private static final Logger log = LoggerFactory.getLogger(UserEmailResolver.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final UtilisateurRepository utilisateurRepository;
    private final InscriptionRepository inscriptionRepository;
    private Function<String, Optional<String>> firebaseAuthEmailProvider;

    @Autowired
    public UserEmailResolver(UtilisateurRepository utilisateurRepository,
                             InscriptionRepository inscriptionRepository) {
        this.utilisateurRepository = utilisateurRepository;
        this.inscriptionRepository = inscriptionRepository;
        this.firebaseAuthEmailProvider = uid -> Optional.empty();
    }

    /** Allows registering a custom Firebase Auth Admin / Identity Toolkit UID-to-email lookup function. */
    public void setFirebaseAuthEmailProvider(Function<String, Optional<String>> provider) {
        this.firebaseAuthEmailProvider = provider != null ? provider : (uid -> Optional.empty());
    }

    public boolean isValidEmail(String email) {
        if (email == null) return false;
        String trimmed = email.trim();
        return !trimmed.isEmpty() && !trimmed.contains("..") && EMAIL_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Resolves a validated email address for the given recipient UID.
     * Never uses the currently authenticated caller's email; strictly uses the recipient's UID.
     */
    public Optional<String> resolveEmailByUid(String recipientUid) {
        if (recipientUid == null || recipientUid.isBlank()) {
            return Optional.empty();
        }

        Optional<Utilisateur> userOpt = utilisateurRepository.findById(recipientUid);
        if (userOpt.isPresent()) {
            String dbEmail = userOpt.get().getEmail();
            if (isValidEmail(dbEmail)) {
                return Optional.of(dbEmail.trim().toLowerCase());
            }
        }

        // Fallback 1: check denormalized Inscription records for this UID
        for (Inscription ins : inscriptionRepository.findByUtilisateurId(recipientUid)) {
            if (isValidEmail(ins.getEmailUtilisateur())) {
                String resolved = ins.getEmailUtilisateur().trim().toLowerCase();
                backfillUserEmail(userOpt.orElse(null), resolved);
                return Optional.of(resolved);
            }
        }

        // Fallback 2: query Firebase Authentication UID lookup provider
        try {
            Optional<String> authEmail = firebaseAuthEmailProvider.apply(recipientUid);
            if (authEmail.isPresent() && isValidEmail(authEmail.get())) {
                String resolved = authEmail.get().trim().toLowerCase();
                backfillUserEmail(userOpt.orElse(null), resolved);
                return Optional.of(resolved);
            }
        } catch (Exception e) {
            log.warn("Firebase Auth email lookup failed for UID [{}]: {}", recipientUid, e.getMessage());
        }

        return Optional.empty();
    }

    public String resolveDisplayNameByUid(String recipientUid) {
        if (recipientUid == null || recipientUid.isBlank()) {
            return "Utilisateur";
        }
        return utilisateurRepository.findById(recipientUid)
                .map(Utilisateur::getNomComplet)
                .filter(n -> !n.isBlank())
                .orElse("Utilisateur");
    }

    private void backfillUserEmail(Utilisateur user, String validEmail) {
        if (user != null && !isValidEmail(user.getEmail())) {
            user.setEmail(validEmail);
            utilisateurRepository.save(user);
        }
    }
}
