package xakariya.classlink.domain.model;

import xakariya.classlink.domain.enums.EmailStatus;
import xakariya.classlink.domain.enums.TypeNotification;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Notification implements Serializable {
    private String id;
    private String utilisateurId;
    private String classeId;
    private String nomClasse;
    private TypeNotification type;
    private String titre;
    private String message;
    private String lienCible;
    private LocalDateTime dateCreation;
    private LocalDateTime dateLecture;

    // Automatic email delivery tracking fields
    private EmailStatus emailStatus;
    private String destinataireEmail;
    private Integer emailAttempts;
    private LocalDateTime emailSentAt;
    private String emailError;

    public Notification() {
        this.dateCreation = LocalDateTime.now();
        this.emailStatus = EmailStatus.PENDING;
        this.emailAttempts = 0;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUtilisateurId() {
        return utilisateurId;
    }

    public void setUtilisateurId(String utilisateurId) {
        this.utilisateurId = utilisateurId;
    }

    public String getClasseId() {
        return classeId;
    }

    public void setClasseId(String classeId) {
        this.classeId = classeId;
    }

    public String getNomClasse() {
        return nomClasse;
    }

    public void setNomClasse(String nomClasse) {
        this.nomClasse = nomClasse;
    }

    public TypeNotification getType() {
        return type;
    }

    public void setType(TypeNotification type) {
        this.type = type;
    }

    public String getTitre() {
        return titre;
    }

    public void setTitre(String titre) {
        this.titre = titre;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLienCible() {
        return lienCible;
    }

    public void setLienCible(String lienCible) {
        this.lienCible = lienCible;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public LocalDateTime getDateLecture() {
        return dateLecture;
    }

    public void setDateLecture(LocalDateTime dateLecture) {
        this.dateLecture = dateLecture;
    }

    public EmailStatus getEmailStatus() {
        return emailStatus;
    }

    public void setEmailStatus(EmailStatus emailStatus) {
        this.emailStatus = emailStatus;
    }

    public String getDestinataireEmail() {
        return destinataireEmail;
    }

    public void setDestinataireEmail(String destinataireEmail) {
        this.destinataireEmail = destinataireEmail;
    }

    public Integer getEmailAttempts() {
        return emailAttempts;
    }

    public void setEmailAttempts(Integer emailAttempts) {
        this.emailAttempts = emailAttempts;
    }

    public LocalDateTime getEmailSentAt() {
        return emailSentAt;
    }

    public void setEmailSentAt(LocalDateTime emailSentAt) {
        this.emailSentAt = emailSentAt;
    }

    public String getEmailError() {
        return emailError;
    }

    public void setEmailError(String emailError) {
        this.emailError = emailError;
    }
}
