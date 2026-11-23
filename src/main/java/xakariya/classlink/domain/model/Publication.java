package xakariya.classlink.domain.model;

import xakariya.classlink.domain.enums.TypePublication;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Publication implements Serializable {
    private String id;
    private String classeId;
    private String auteurId;
    private String nomAuteur;
    private String photoUrlAuteur;
    private TypePublication type;
    private String titre;
    private String contenu;
    private LocalDateTime datePublication;
    private LocalDateTime dateLimite;
    private Double bareme;
    private List<PieceJointe> piecesJointes;

    public Publication() {
        this.datePublication = LocalDateTime.now();
        this.piecesJointes = new ArrayList<>();
        this.type = TypePublication.ANNONCE;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClasseId() {
        return classeId;
    }

    public void setClasseId(String classeId) {
        this.classeId = classeId;
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

    public TypePublication getType() {
        return type;
    }

    public void setType(TypePublication type) {
        this.type = type;
    }

    public String getTitre() {
        return titre;
    }

    public void setTitre(String titre) {
        this.titre = titre;
    }

    public String getContenu() {
        return contenu;
    }

    public void setContenu(String contenu) {
        this.contenu = contenu;
    }

    public LocalDateTime getDatePublication() {
        return datePublication;
    }

    public void setDatePublication(LocalDateTime datePublication) {
        this.datePublication = datePublication;
    }

    public LocalDateTime getDateLimite() {
        return dateLimite;
    }

    public void setDateLimite(LocalDateTime dateLimite) {
        this.dateLimite = dateLimite;
    }

    public Double getBareme() {
        return bareme;
    }

    public void setBareme(Double bareme) {
        this.bareme = bareme;
    }

    public List<PieceJointe> getPiecesJointes() {
        return piecesJointes;
    }

    public void setPiecesJointes(List<PieceJointe> piecesJointes) {
        this.piecesJointes = piecesJointes != null ? piecesJointes : new ArrayList<>();
    }
}
