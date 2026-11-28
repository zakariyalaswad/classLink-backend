package xakariya.classlink.service;

import org.springframework.stereotype.Service;
import xakariya.classlink.domain.enums.PermissionPublication;
import xakariya.classlink.domain.enums.RoleClasse;
import xakariya.classlink.domain.enums.TypeNotification;
import xakariya.classlink.domain.enums.TypePublication;
import xakariya.classlink.domain.model.*;
import xakariya.classlink.repository.ClasseRepository;
import xakariya.classlink.repository.InscriptionRepository;
import xakariya.classlink.repository.PublicationRepository;
import xakariya.classlink.repository.UtilisateurRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PublicationService {

    private final PublicationRepository publicationRepository;
    private final ClasseRepository classeRepository;
    private final InscriptionRepository inscriptionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;

    public PublicationService(PublicationRepository publicationRepository,
                              ClasseRepository classeRepository,
                              InscriptionRepository inscriptionRepository,
                              UtilisateurRepository utilisateurRepository,
                              NotificationService notificationService) {
        this.publicationRepository = publicationRepository;
        this.classeRepository = classeRepository;
        this.inscriptionRepository = inscriptionRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.notificationService = notificationService;
    }

    /**
     * Use Case: «include» Vérifier le droit de publier une annonce
     */
    public boolean verifierDroitPublierAnnonce(String classeId, String utilisateurId) {
        Classe classe = classeRepository.findById(classeId)
                .orElseThrow(() -> new IllegalArgumentException("Classe non trouvée: " + classeId));

        if (Boolean.TRUE.equals(classe.getArchivee())) {
            return false;
        }

        Inscription inscription = inscriptionRepository.findByUtilisateurIdAndClasseId(utilisateurId, classeId)
                .orElseThrow(() -> new SecurityException("L'utilisateur n'est pas membre de cette classe"));

        if (inscription.getRole() == RoleClasse.ENSEIGNANT) {
            return true;
        }

        ParametresClasse params = classe.getParametres();
        return params == null || params.getPublicationAnnonces() == PermissionPublication.TOUS_LES_MEMBRES;
    }

    /**
     * Use Case: Publier une annonce
     */
    public Publication publierAnnonce(String classeId, String auteurId, String titre, String contenu, List<PieceJointe> piecesJointes) {
        if (!verifierDroitPublierAnnonce(classeId, auteurId)) {
            throw new SecurityException("Vous n'avez pas l'autorisation de publier une annonce dans cette classe");
        }

        Utilisateur auteur = utilisateurRepository.findById(auteurId)
                .orElseThrow(() -> new IllegalArgumentException("Auteur non trouvé: " + auteurId));
        Classe classe = classeRepository.findById(classeId).orElseThrow();

        Publication publication = new Publication();
        publication.setClasseId(classeId);
        publication.setAuteurId(auteurId);
        publication.setNomAuteur(auteur.getNomComplet());
        publication.setPhotoUrlAuteur(auteur.getPhotoUrl());
        publication.setType(TypePublication.ANNONCE);
        publication.setTitre(titre != null && !titre.isBlank() ? titre : "Annonce");
        publication.setContenu(contenu);
        publication.setPiecesJointes(piecesJointes);
        publication.setDatePublication(LocalDateTime.now());

        Publication saved = publicationRepository.save(publication);

        // Notifier tous les membres
        notifierMembres(classe, auteurId, TypeNotification.NOUVELLE_ANNONCE,
                "Nouvelle annonce : " + saved.getTitre(),
                auteur.getNomComplet() + " a publié une annonce dans " + classe.getNom(),
                "/classes/" + classeId);

        return saved;
    }

    /**
     * Use Case: Publier un cours (Enseignant)
     */
    public Publication publierCours(String classeId, String enseignantId, String titre, String contenu, List<PieceJointe> piecesJointes) {
        verifierRoleEnseignant(classeId, enseignantId);

        Utilisateur enseignant = utilisateurRepository.findById(enseignantId)
                .orElseThrow(() -> new IllegalArgumentException("Enseignant non trouvé: " + enseignantId));
        Classe classe = classeRepository.findById(classeId).orElseThrow();

        Publication cours = new Publication();
        cours.setClasseId(classeId);
        cours.setAuteurId(enseignantId);
        cours.setNomAuteur(enseignant.getNomComplet());
        cours.setPhotoUrlAuteur(enseignant.getPhotoUrl());
        cours.setType(TypePublication.COURS);
        cours.setTitre(titre);
        cours.setContenu(contenu);
        cours.setPiecesJointes(piecesJointes);
        cours.setDatePublication(LocalDateTime.now());

        Publication saved = publicationRepository.save(cours);

        notifierMembres(classe, enseignantId, TypeNotification.NOUVEAU_COURS,
                "Nouveau cours : " + saved.getTitre(),
                enseignant.getNomComplet() + " a publié un support de cours",
                "/classes/" + classeId + "?tab=WORK");

        return saved;
    }

    /**
     * Use Case: Créer un devoir (Enseignant)
     */
    public Publication creerDevoir(String classeId, String enseignantId, String titre, String instructions,
                                   LocalDateTime dateLimite, Double bareme, List<PieceJointe> piecesJointes) {
        verifierRoleEnseignant(classeId, enseignantId);

        Utilisateur enseignant = utilisateurRepository.findById(enseignantId)
                .orElseThrow(() -> new IllegalArgumentException("Enseignant non trouvé: " + enseignantId));
        Classe classe = classeRepository.findById(classeId).orElseThrow();

        Publication devoir = new Publication();
        devoir.setClasseId(classeId);
        devoir.setAuteurId(enseignantId);
        devoir.setNomAuteur(enseignant.getNomComplet());
        devoir.setPhotoUrlAuteur(enseignant.getPhotoUrl());
        devoir.setType(TypePublication.DEVOIR);
        devoir.setTitre(titre);
        devoir.setContenu(instructions);
        devoir.setDateLimite(dateLimite);
        devoir.setBareme(bareme != null ? bareme : 20.0);
        devoir.setPiecesJointes(piecesJointes);
        devoir.setDatePublication(LocalDateTime.now());

        Publication saved = publicationRepository.save(devoir);

        notifierMembres(classe, enseignantId, TypeNotification.NOUVEAU_DEVOIR,
                "Nouveau devoir : " + saved.getTitre(),
                "Un nouveau devoir à rendre avant le " + dateLimite + " est disponible",
                "/classes/" + classeId + "?tab=WORK");

        return saved;
    }

    /**
     * Use Case: Consulter les publications
     */
    public List<Publication> consulterPublications(String classeId, String utilisateurId) {
        // Vérifier l'inscription
        if (!inscriptionRepository.findByUtilisateurIdAndClasseId(utilisateurId, classeId).isPresent()) {
            throw new SecurityException("Accès refusé : vous n'êtes pas inscrit à cette classe");
        }
        return publicationRepository.findByClasseId(classeId);
    }

    public List<Publication> consulterPublicationsParType(String classeId, TypePublication type, String utilisateurId) {
        if (!inscriptionRepository.findByUtilisateurIdAndClasseId(utilisateurId, classeId).isPresent()) {
            throw new SecurityException("Accès refusé : vous n'êtes pas inscrit à cette classe");
        }
        return publicationRepository.findByClasseIdAndType(classeId, type);
    }

    public Publication getPublicationById(String id) {
        return publicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Publication introuvable: " + id));
    }

    private void verifierRoleEnseignant(String classeId, String utilisateurId) {
        Classe classe = classeRepository.findById(classeId)
                .orElseThrow(() -> new IllegalArgumentException("Classe non trouvée: " + classeId));
        if (Boolean.TRUE.equals(classe.getArchivee())) {
            throw new IllegalStateException("La classe est archivée");
        }
        var ins = inscriptionRepository.findByUtilisateurIdAndClasseId(utilisateurId, classeId);
        if (ins.isEmpty() || ins.get().getRole() != RoleClasse.ENSEIGNANT) {
            throw new SecurityException("Action réservée aux enseignants de la classe");
        }
    }

    private void notifierMembres(Classe classe, String auteurId, TypeNotification type,
                                String titre, String message, String lien) {
        List<Inscription> inscriptions = inscriptionRepository.findByClasseId(classe.getId());
        for (Inscription ins : inscriptions) {
            if (!ins.getUtilisateurId().equals(auteurId) && Boolean.TRUE.equals(ins.getNotificationsActives())) {
                notificationService.envoyerNotification(
                        ins.getUtilisateurId(),
                        classe.getId(),
                        classe.getNom(),
                        type,
                        titre,
                        message,
                        lien
                );
            }
        }
    }
}
