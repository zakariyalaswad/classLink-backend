package xakariya.classlink.domain.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Utilisateur implements Serializable {
    private String id;
    private String nom;
    private String prenom;
    private String email;
    private String photoUrl;
    private LocalDateTime dateCreation;

    public Utilisateur() {
        this.dateCreation = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public String getNomComplet() {
        return ((prenom != null ? prenom : "") + " " + (nom != null ? nom : "")).trim();
    }
}
