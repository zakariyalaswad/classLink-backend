package xakariya.classlink.dto;

import xakariya.classlink.domain.enums.PermissionCommentaire;
import xakariya.classlink.domain.enums.PermissionPublication;
import xakariya.classlink.domain.enums.TypeNotification;
import xakariya.classlink.domain.enums.TypePublication;
import xakariya.classlink.domain.model.PieceJointe;

import java.time.LocalDateTime;
import java.util.List;

public class Dtos {

    public static class RegisterRequest {
        public String nom;
        public String prenom;
    }

    public static class UpdateProfileRequest {
        public String nom;
        public String prenom;
        public String photoUrl;
    }

    public static class CreateClassRequest {
        public String nom;
        public String description;
        public String matiere;
    }

    public static class JoinClassRequest {
        public String codeAcces;
    }

    public static class UpdateSettingsRequest {
        public PermissionPublication publicationAnnonces;
        public PermissionCommentaire commentaires;
        public Boolean codeAccesActif;
    }

    public static class CreatePublicationRequest {
        public TypePublication type;
        public String titre;
        public String contenu;
        public LocalDateTime dateLimite;
        public Double bareme;
        public List<PieceJointe> piecesJointes;
    }

    public static class CreateCommentRequest {
        public String contenu;
    }

    public static class SubmitAssignmentRequest {
        public String texte;
        public List<PieceJointe> piecesJointes;
    }

    public static class GradeSubmissionRequest {
        public Double note;
        public String correction;
    }

    public static class UpdatePreferenceRequest {
        public TypeNotification type;
        public Boolean dansApplication;
        public Boolean parEmail;
    }
}
