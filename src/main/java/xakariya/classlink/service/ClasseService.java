package xakariya.classlink.service;

import org.springframework.stereotype.Service;
import xakariya.classlink.domain.enums.PermissionCommentaire;
import xakariya.classlink.domain.enums.PermissionPublication;
import xakariya.classlink.domain.enums.RoleClasse;
import xakariya.classlink.domain.model.*;
import xakariya.classlink.repository.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ClasseService {

    private final ClasseRepository classeRepository;
    private final InscriptionRepository inscriptionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PublicationRepository publicationRepository;
    private final CommentaireRepository commentaireRepository;
    private final RemiseRepository remiseRepository;

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    public ClasseService(ClasseRepository classeRepository,
                         InscriptionRepository inscriptionRepository,
                         UtilisateurRepository utilisateurRepository,
                         PublicationRepository publicationRepository,
                         CommentaireRepository commentaireRepository,
                         RemiseRepository remiseRepository) {
        this.classeRepository = classeRepository;
        this.inscriptionRepository = inscriptionRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.publicationRepository = publicationRepository;
        this.commentaireRepository = commentaireRepository;
        this.remiseRepository = remiseRepository;
    }

    public Classe creerClasse(String nom, String description, String matiere, String createurId) {
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("Le nom de la classe est requis");
        }

        Utilisateur createur = utilisateurRepository.findById(createurId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé: " + createurId));

        Classe classe = new Classe();
        classe.setNom(nom);
        classe.setDescription(description != null ? description : "");
        classe.setMatiere(matiere != null ? matiere : "Général");
        classe.setCodeAcces(genererCodeAccesUnique());
        classe.setArchivee(false);
        classe.setDateCreation(LocalDateTime.now());

        ParametresClasse params = new ParametresClasse();
        params.setId(UUID.randomUUID().toString());
        params.setCodeAccesActif(true);
        params.setPublicationAnnonces(PermissionPublication.TOUS_LES_MEMBRES);
        params.setCommentaires(PermissionCommentaire.TOUS_LES_MEMBRES);
        classe.setParametres(params);

        Classe savedClasse = classeRepository.save(classe);

        Inscription inscription = new Inscription();
        inscription.setClasseId(savedClasse.getId());
        inscription.setUtilisateurId(createur.getId());
        inscription.setRole(RoleClasse.ENSEIGNANT);
        inscription.setNomUtilisateur(createur.getNom());
        inscription.setPrenomUtilisateur(createur.getPrenom());
        inscription.setEmailUtilisateur(createur.getEmail());
        inscription.setPhotoUrlUtilisateur(createur.getPhotoUrl());
        inscription.setDateInscription(LocalDateTime.now());
        inscriptionRepository.save(inscription);

        return savedClasse;
    }

    public Inscription rejoindreClasseParCode(String codeAcces, String utilisateurId) {
        if (codeAcces == null || codeAcces.isBlank()) {
            throw new IllegalArgumentException("Le code d'accès est requis");
        }

        Classe classe = classeRepository.findByCodeAcces(codeAcces.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Aucune classe ne correspond à ce code"));

        if (Boolean.TRUE.equals(classe.getArchivee())) {
            throw new IllegalStateException("Cette classe est archivée et ne peut plus être rejointe");
        }

        ParametresClasse params = classe.getParametres();
        if (params != null && Boolean.FALSE.equals(params.getCodeAccesActif())) {
            throw new IllegalStateException("Les inscriptions par code sont actuellement désactivées pour cette classe");
        }

        Utilisateur u = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé: " + utilisateurId));

        var existing = inscriptionRepository.findByUtilisateurIdAndClasseId(utilisateurId, classe.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Inscription inscription = new Inscription();
        inscription.setClasseId(classe.getId());
        inscription.setUtilisateurId(u.getId());
        inscription.setRole(RoleClasse.ETUDIANT);
        inscription.setNomUtilisateur(u.getNom());
        inscription.setPrenomUtilisateur(u.getPrenom());
        inscription.setEmailUtilisateur(u.getEmail());
        inscription.setPhotoUrlUtilisateur(u.getPhotoUrl());
        inscription.setDateInscription(LocalDateTime.now());

        return inscriptionRepository.save(inscription);
    }

    public Classe parametrerClasse(String classeId, String utilisateurId, ParametresClasse nouveauxParams) {
        Classe classe = getClasseValidee(classeId);
        verifierRoleEnseignant(classeId, utilisateurId);

        if (nouveauxParams != null) {
            ParametresClasse params = classe.getParametres() != null ? classe.getParametres() : new ParametresClasse();
            if (nouveauxParams.getPublicationAnnonces() != null) {
                params.setPublicationAnnonces(nouveauxParams.getPublicationAnnonces());
            }
            if (nouveauxParams.getCommentaires() != null) {
                params.setCommentaires(nouveauxParams.getCommentaires());
            }
            if (nouveauxParams.getCodeAccesActif() != null) {
                params.setCodeAccesActif(nouveauxParams.getCodeAccesActif());
            }
            classe.setParametres(params);
        }

        return classeRepository.save(classe);
    }

    public Classe archiverClasse(String classeId, String utilisateurId, boolean archivee) {
        Classe classe = getClasseValidee(classeId);
        verifierRoleEnseignant(classeId, utilisateurId);

        classe.setArchivee(archivee);
        return classeRepository.save(classe);
    }

    public void supprimerClasse(String classeId, String utilisateurId) {
        getClasseValidee(classeId);
        verifierRoleEnseignant(classeId, utilisateurId);

        for (Publication pub : publicationRepository.findByClasseId(classeId)) {
            for (Commentaire c : commentaireRepository.findByPublicationId(pub.getId())) {
                commentaireRepository.deleteById(c.getId());
            }
            for (Remise r : remiseRepository.findByDevoirId(pub.getId())) {
                remiseRepository.deleteById(r.getId());
            }
            publicationRepository.deleteById(pub.getId());
        }
        for (Inscription ins : inscriptionRepository.findByClasseId(classeId)) {
            inscriptionRepository.deleteById(ins.getId());
        }
        classeRepository.deleteById(classeId);
    }

    public String regenererCodeAcces(String classeId, String utilisateurId) {
        Classe classe = getClasseValidee(classeId);
        verifierRoleEnseignant(classeId, utilisateurId);

        String nouveauCode = genererCodeAccesUnique();
        classe.setCodeAcces(nouveauCode);
        classeRepository.save(classe);
        return nouveauCode;
    }

    public void quitterClasse(String classeId, String utilisateurId) {
        var ins = inscriptionRepository.findByUtilisateurIdAndClasseId(utilisateurId, classeId)
                .orElseThrow(() -> new IllegalArgumentException("Vous n'êtes pas membre de cette classe"));
        if (ins.getRole() == RoleClasse.ENSEIGNANT) {
            throw new IllegalStateException("Un enseignant ne peut pas quitter sa classe (archivez-la à la place)");
        }
        inscriptionRepository.deleteByUtilisateurIdAndClasseId(utilisateurId, classeId);
    }

    public List<Classe> getClassesPourUtilisateur(String utilisateurId) {
        List<Inscription> inscriptions = inscriptionRepository.findByUtilisateurId(utilisateurId);
        List<Classe> result = new ArrayList<>();
        for (Inscription ins : inscriptions) {
            classeRepository.findById(ins.getClasseId()).ifPresent(result::add);
        }
        return result;
    }

    public Optional<Classe> getClasseById(String id) {
        return classeRepository.findById(id);
    }

    public List<Inscription> getMembresClasse(String classeId) {
        return inscriptionRepository.findByClasseId(classeId);
    }

    private Classe getClasseValidee(String classeId) {
        return classeRepository.findById(classeId)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable: " + classeId));
    }

    public void verifierMembre(String classeId, String utilisateurId) {
        if (inscriptionRepository.findByUtilisateurIdAndClasseId(utilisateurId, classeId).isEmpty()) {
            throw new SecurityException("Vous n'êtes pas membre de cette classe");
        }
    }

    public void verifierRoleEnseignant(String classeId, String utilisateurId) {
        var insOpt = inscriptionRepository.findByUtilisateurIdAndClasseId(utilisateurId, classeId);
        if (insOpt.isEmpty() || insOpt.get().getRole() != RoleClasse.ENSEIGNANT) {
            throw new SecurityException("Action réservée aux enseignants de la classe");
        }
    }

    private String genererCodeAccesUnique() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
