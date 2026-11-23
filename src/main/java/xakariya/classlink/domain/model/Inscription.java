package xakariya.classlink.domain.model;

import xakariya.classlink.domain.enums.RoleClasse;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Inscription implements Serializable {
    private String id;
    private String utilisateurId;
    private String classeId;
    private RoleClasse role;
    private Boolean notificationsActives;
    private LocalDateTime dateInscription;
    private String nomUtilisateur;
    private String prenomUtilisateur;
    private String emailUtilisateur;
    private String photoUrlUtilisateur;

    public Inscription() {
        this.notificationsActives = true;
        this.dateInscription = LocalDateTime.now();
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

    public RoleClasse getRole() {
        return role;
    }

    public void setRole(RoleClasse role) {
        this.role = role;
    }

    public Boolean getNotificationsActives() {
        return notificationsActives;
    }

    public void setNotificationsActives(Boolean notificationsActives) {
        this.notificationsActives = notificationsActives;
    }

    public LocalDateTime getDateInscription() {
        return dateInscription;
    }

    public void setDateInscription(LocalDateTime dateInscription) {
        this.dateInscription = dateInscription;
    }

    public String getNomUtilisateur() {
        return nomUtilisateur;
    }

    public void setNomUtilisateur(String nomUtilisateur) {
        this.nomUtilisateur = nomUtilisateur;
    }

    public String getPrenomUtilisateur() {
        return prenomUtilisateur;
    }

    public void setPrenomUtilisateur(String prenomUtilisateur) {
        this.prenomUtilisateur = prenomUtilisateur;
    }

    public String getEmailUtilisateur() {
        return emailUtilisateur;
    }

    public void setEmailUtilisateur(String emailUtilisateur) {
        this.emailUtilisateur = emailUtilisateur;
    }

    public String getPhotoUrlUtilisateur() {
        return photoUrlUtilisateur;
    }

    public void setPhotoUrlUtilisateur(String photoUrlUtilisateur) {
        this.photoUrlUtilisateur = photoUrlUtilisateur;
    }
}
