package xakariya.classlink.service;

import org.springframework.stereotype.Service;
import xakariya.classlink.domain.enums.TypeNotification;
import xakariya.classlink.domain.model.Inscription;
import xakariya.classlink.domain.model.PreferenceNotification;
import xakariya.classlink.domain.model.Utilisateur;
import xakariya.classlink.repository.InscriptionRepository;
import xakariya.classlink.repository.PreferenceNotificationRepository;
import xakariya.classlink.repository.UtilisateurRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final PreferenceNotificationRepository preferenceRepository;
    private final InscriptionRepository inscriptionRepository;

    public UtilisateurService(UtilisateurRepository utilisateurRepository,
                              PreferenceNotificationRepository preferenceRepository,
                              InscriptionRepository inscriptionRepository) {
        this.utilisateurRepository = utilisateurRepository;
        this.preferenceRepository = preferenceRepository;
        this.inscriptionRepository = inscriptionRepository;
    }

    public Utilisateur gererProfil(String id, String nom, String prenom, String photoUrl) {
        Utilisateur u = utilisateurRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé: " + id));

        if (nom != null && !nom.isBlank()) u.setNom(nom.trim());
        if (prenom != null && !prenom.isBlank()) u.setPrenom(prenom.trim());
        if (photoUrl != null) {
            u.setPhotoUrl(photoUrl.isBlank() ? null : photoUrl);
        }

        Utilisateur saved = utilisateurRepository.save(u);

        for (Inscription ins : inscriptionRepository.findByUtilisateurId(id)) {
            ins.setNomUtilisateur(saved.getNom());
            ins.setPrenomUtilisateur(saved.getPrenom());
            ins.setPhotoUrlUtilisateur(saved.getPhotoUrl());
            inscriptionRepository.save(ins);
        }

        return saved;
    }

    public List<PreferenceNotification> getPreferences(String utilisateurId) {
        List<PreferenceNotification> existing = preferenceRepository.findByUtilisateurId(utilisateurId);
        if (existing.isEmpty()) {
            List<PreferenceNotification> defaults = new ArrayList<>();
            for (TypeNotification type : TypeNotification.values()) {
                PreferenceNotification p = new PreferenceNotification(null, utilisateurId, type, true, false);
                defaults.add(preferenceRepository.save(p));
            }
            return defaults;
        }
        return existing;
    }

    public PreferenceNotification modifierPreference(String utilisateurId, TypeNotification type,
                                                     boolean dansApp, boolean parEmail) {
        PreferenceNotification p = preferenceRepository.findByUtilisateurIdAndType(utilisateurId, type)
                .orElse(new PreferenceNotification(null, utilisateurId, type, dansApp, parEmail));

        p.setDansApplication(dansApp);
        p.setParEmail(parEmail);
        return preferenceRepository.save(p);
    }
}
