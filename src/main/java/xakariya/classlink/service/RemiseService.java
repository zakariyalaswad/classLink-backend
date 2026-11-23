package xakariya.classlink.service;

import org.springframework.stereotype.Service;
import xakariya.classlink.domain.enums.RoleClasse;
import xakariya.classlink.domain.enums.TypeNotification;
import xakariya.classlink.domain.enums.TypePublication;
import xakariya.classlink.domain.model.*;
import xakariya.classlink.repository.*;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RemiseService {

    private final RemiseRepository remiseRepository;
    private final PublicationRepository publicationRepository;
    private final ClasseRepository classeRepository;
    private final InscriptionRepository inscriptionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;

    public RemiseService(RemiseRepository remiseRepository,
                         PublicationRepository publicationRepository,
                         ClasseRepository classeRepository,
                         InscriptionRepository inscriptionRepository,
                         UtilisateurRepository utilisateurRepository,
                         NotificationService notificationService) {
        this.remiseRepository = remiseRepository;
        this.publicationRepository = publicationRepository;
        this.classeRepository = classeRepository;
        this.inscriptionRepository = inscriptionRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.notificationService = notificationService;
    }

    /**
     * Use Case: Soumettre une remise (Étudiant)
     */
    public Remise soumettreRemise(String devoirId, String etudiantId, String texte, List<PieceJointe> piecesJointes) {
        Publication devoir = publicationRepository.findById(devoirId)
                .orElseThrow(() -> new IllegalArgumentException("Devoir introuvable: " + devoirId));

        if (devoir.getType() != TypePublication.DEVOIR) {
            throw new IllegalArgumentException("Cette publication n'est pas un devoir");
        }

        Classe classe = classeRepository.findById(devoir.getClasseId())
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable"));

        if (Boolean.TRUE.equals(classe.getArchivee())) {
            throw new IllegalStateException("La classe est archivée, soumission impossible");
        }

        if (inscriptionRepository.findByUtilisateurIdAndClasseId(etudiantId, classe.getId()).isEmpty()) {
            throw new SecurityException("Vous n'êtes pas inscrit à cette classe");
        }

        Utilisateur etudiant = utilisateurRepository.findById(etudiantId)
                .orElseThrow(() -> new IllegalArgumentException("Étudiant introuvable"));

        // Vérifier si une remise existe déjà
        Remise remise = remiseRepository.findByDevoirIdAndEtudiantId(devoirId, etudiantId)
                .orElse(new Remise());

        remise.setDevoirId(devoirId);
        remise.setEtudiantId(etudiantId);
        remise.setNomEtudiant(etudiant.getNomComplet());
        remise.setEmailEtudiant(etudiant.getEmail());
        remise.setPhotoUrlEtudiant(etudiant.getPhotoUrl());
        remise.setTexte(texte);
        remise.setPiecesJointes(piecesJointes);
        remise.setDateRemise(LocalDateTime.now());

        Remise saved = remiseRepository.save(remise);

        // Notifier l'enseignant créateur du devoir
        notificationService.envoyerNotification(
                devoir.getAuteurId(),
                classe.getId(),
                classe.getNom(),
                TypeNotification.NOUVELLE_REMISE,
                "Nouvelle remise : " + etudiant.getNomComplet(),
                etudiant.getNomComplet() + " a remis son devoir pour \"" + devoir.getTitre() + "\"",
                "/assignments/" + devoirId
        );

        return saved;
    }

    /**
     * Use Case: Corriger et noter une remise (Enseignant)
     */
    public Remise corrigerEtNoterRemise(String remiseId, String enseignantId, Double note, String correction) {
        Remise remise = remiseRepository.findById(remiseId)
                .orElseThrow(() -> new IllegalArgumentException("Remise introuvable: " + remiseId));

        Publication devoir = publicationRepository.findById(remise.getDevoirId())
                .orElseThrow(() -> new IllegalArgumentException("Devoir introuvable"));

        Classe classe = classeRepository.findById(devoir.getClasseId()).orElseThrow();
        if (Boolean.TRUE.equals(classe.getArchivee())) {
            throw new IllegalStateException("La classe est archivée, correction impossible");
        }

        Inscription ins = inscriptionRepository.findByUtilisateurIdAndClasseId(enseignantId, classe.getId())
                .orElseThrow(() -> new SecurityException("Action réservée aux enseignants de la classe"));

        if (ins.getRole() != RoleClasse.ENSEIGNANT) {
            throw new SecurityException("Seuls les enseignants peuvent noter et corriger");
        }

        Utilisateur enseignant = utilisateurRepository.findById(enseignantId)
                .orElseThrow(() -> new IllegalArgumentException("Enseignant introuvable"));

        if (note != null && devoir.getBareme() != null && (note < 0 || note > devoir.getBareme())) {
            throw new IllegalArgumentException("La note doit être comprise entre 0 et " + devoir.getBareme());
        }

        remise.setNote(note);
        remise.setCorrection(correction);
        remise.setCorrecteurId(enseignantId);
        remise.setNomCorrecteur(enseignant.getNomComplet());
        remise.setDateCorrection(LocalDateTime.now());

        Remise saved = remiseRepository.save(remise);

        // Notifier l'étudiant
        notificationService.envoyerNotification(
                remise.getEtudiantId(),
                classe.getId(),
                classe.getNom(),
                TypeNotification.NOTE_PUBLIEE,
                "Note publiée pour \"" + devoir.getTitre() + "\"",
                "Votre note : " + (note != null ? note + "/" + devoir.getBareme() : "corrigé"),
                "/assignments/" + devoir.getId()
        );

        return saved;
    }

    /**
     * Use Case: Consulter ses notes et corrections (Étudiant)
     */
    public List<Remise> consulterNotesEtCorrections(String etudiantId) {
        return remiseRepository.findByEtudiantId(etudiantId);
    }

    public List<Remise> getRemisesPourDevoir(String devoirId) {
        return remiseRepository.findByDevoirId(devoirId);
    }

    public Remise getRemisePourDevoirEtEtudiant(String devoirId, String etudiantId) {
        return remiseRepository.findByDevoirIdAndEtudiantId(devoirId, etudiantId).orElse(null);
    }
}
