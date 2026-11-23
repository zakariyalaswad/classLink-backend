package xakariya.classlink.domain.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Classe implements Serializable {
    private String id;
    private String nom;
    private String description;
    private String matiere;
    private String codeAcces;
    private Boolean archivee;
    private LocalDateTime dateCreation;
    private ParametresClasse parametres;

    public Classe() {
        this.archivee = false;
        this.dateCreation = LocalDateTime.now();
        this.parametres = new ParametresClasse();
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMatiere() {
        return matiere;
    }

    public void setMatiere(String matiere) {
        this.matiere = matiere;
    }

    public String getCodeAcces() {
        return codeAcces;
    }

    public void setCodeAcces(String codeAcces) {
        this.codeAcces = codeAcces;
    }

    public Boolean getArchivee() {
        return archivee;
    }

    public void setArchivee(Boolean archivee) {
        this.archivee = archivee;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public ParametresClasse getParametres() {
        return parametres;
    }

    public void setParametres(ParametresClasse parametres) {
        this.parametres = parametres;
    }
}
