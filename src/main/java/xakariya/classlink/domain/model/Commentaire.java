package xakariya.classlink.domain.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Commentaire implements Serializable {
    private String id;
    private String publicationId;
    private String auteurId;
    private String nomAuteur;
    private String photoUrlAuteur;
    private String contenu;
    private LocalDateTime dateCreation;

    public Commentaire() {
        this.dateCreation = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPublicationId() {
        return publicationId;
    }

    public void setPublicationId(String publicationId) {
        this.publicationId = publicationId;
    }

    public String getAuteurId() {
        return auteurId;
    }

    public void setAuteurId(String auteurId) {
        this.auteurId = auteurId;
    }

    public String getNomAuteur() {
        return nomAuteur;
    }

    public void setNomAuteur(String nomAuteur) {
        this.nomAuteur = nomAuteur;
    }

    public String getPhotoUrlAuteur() {
        return photoUrlAuteur;
    }

    public void setPhotoUrlAuteur(String photoUrlAuteur) {
        this.photoUrlAuteur = photoUrlAuteur;
    }

    public String getContenu() {
        return contenu;
    }

    public void setContenu(String contenu) {
        this.contenu = contenu;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }
}
