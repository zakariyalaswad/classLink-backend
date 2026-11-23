package xakariya.classlink.service;

import org.springframework.stereotype.Service;
import xakariya.classlink.domain.enums.PermissionCommentaire;
import xakariya.classlink.domain.enums.RoleClasse;
import xakariya.classlink.domain.enums.TypeNotification;
import xakariya.classlink.domain.model.*;
import xakariya.classlink.repository.*;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CommentaireService {

    private final CommentaireRepository commentaireRepository;
    private final PublicationRepository publicationRepository;
    private final ClasseRepository classeRepository;
    private final InscriptionRepository inscriptionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;

    public CommentaireService(CommentaireRepository commentaireRepository,
                              PublicationRepository publicationRepository,
                              ClasseRepository classeRepository,
                              InscriptionRepository inscriptionRepository,
                              UtilisateurRepository utilisateurRepository,
                              NotificationService notificationService) {
        this.commentaireRepository = commentaireRepository;
        this.publicationRepository = publicationRepository;
        this.classeRepository = classeRepository;
        this.inscriptionRepository = inscriptionRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.notificationService = notificationService;
    }

    /**
     * Use Case: «include» Vérifier le droit de commenter
     */
    public boolean verifierDroitCommenter(String publicationId, String utilisateurId) {
        Publication pub = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new IllegalArgumentException("Publication introuvable: " + publicationId));

        Classe classe = classeRepository.findById(pub.getClasseId())
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable"));

        if (Boolean.TRUE.equals(classe.getArchivee())) {
            return false;
        }

        Inscription ins = inscriptionRepository.findByUtilisateurIdAndClasseId(utilisateurId, classe.getId())
                .orElseThrow(() -> new SecurityException("Non membre de la classe"));

        ParametresClasse params = classe.getParametres();
        if (params == null) return true;

        if (params.getCommentaires() == PermissionCommentaire.PERSONNE) {
            return false;
        }
        if (params.getCommentaires() == PermissionCommentaire.ENSEIGNANTS) {
            return ins.getRole() == RoleClasse.ENSEIGNANT;
        }
        return true;
    }

    /**
     * Use Case: Commenter une publication
     */
    public Commentaire commenterPublication(String publicationId, String auteurId, String contenu) {
        if (contenu == null || contenu.isBlank()) {
            throw new IllegalArgumentException("Le contenu du commentaire ne peut pas être vide");
        }

        if (!verifierDroitCommenter(publicationId, auteurId)) {
            throw new SecurityException("Vous n'avez pas l'autorisation de commenter cette publication");
        }

        Publication publication = publicationRepository.findById(publicationId).orElseThrow();
        Utilisateur auteur = utilisateurRepository.findById(auteurId).orElseThrow();

        Commentaire c = new Commentaire();
        c.setPublicationId(publicationId);
        c.setAuteurId(auteurId);
        c.setNomAuteur(auteur.getNomComplet());
        c.setPhotoUrlAuteur(auteur.getPhotoUrl());
        c.setContenu(contenu);
        c.setDateCreation(LocalDateTime.now());

        Commentaire saved = commentaireRepository.save(c);

        // Notifier l'auteur de la publication si différent
        if (!publication.getAuteurId().equals(auteurId)) {
            notificationService.envoyerNotification(
                    publication.getAuteurId(),
                    publication.getClasseId(),
                    "",
                    TypeNotification.NOUVEAU_COMMENTAIRE,
                    "Nouveau commentaire de " + auteur.getNomComplet(),
                    auteur.getNomComplet() + " a commenté : \"" + contenu + "\"",
                    "/classes/" + publication.getClasseId()
            );
        }

        return saved;
    }

    public List<Commentaire> getCommentaires(String publicationId) {
        return commentaireRepository.findByPublicationId(publicationId);
    }
}
