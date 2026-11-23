package xakariya.classlink.domain.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Remise implements Serializable {
    private String id;
    private String devoirId;
    private String etudiantId;
    private String nomEtudiant;
    private String emailEtudiant;
    private String photoUrlEtudiant;
    private String texte;
    private LocalDateTime dateRemise;
    private Double note;
    private String correction;
    private LocalDateTime dateCorrection;
    private String correcteurId;
    private String nomCorrecteur;
    private List<PieceJointe> piecesJointes;

    public Remise() {
        this.dateRemise = LocalDateTime.now();
        this.piecesJointes = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDevoirId() {
        return devoirId;
    }

    public void setDevoirId(String devoirId) {
        this.devoirId = devoirId;
    }

    public String getEtudiantId() {
        return etudiantId;
    }

    public void setEtudiantId(String etudiantId) {
        this.etudiantId = etudiantId;
    }

    public String getNomEtudiant() {
        return nomEtudiant;
    }

    public void setNomEtudiant(String nomEtudiant) {
        this.nomEtudiant = nomEtudiant;
    }

    public String getEmailEtudiant() {
        return emailEtudiant;
    }

    public void setEmailEtudiant(String emailEtudiant) {
        this.emailEtudiant = emailEtudiant;
    }

    public String getPhotoUrlEtudiant() {
        return photoUrlEtudiant;
    }

    public void setPhotoUrlEtudiant(String photoUrlEtudiant) {
        this.photoUrlEtudiant = photoUrlEtudiant;
    }

    public String getTexte() {
        return texte;
    }

    public void setTexte(String texte) {
        this.texte = texte;
    }

    public LocalDateTime getDateRemise() {
        return dateRemise;
    }

    public void setDateRemise(LocalDateTime dateRemise) {
        this.dateRemise = dateRemise;
    }

    public Double getNote() {
        return note;
    }

    public void setNote(Double note) {
        this.note = note;
    }

    public String getCorrection() {
        return correction;
    }

    public void setCorrection(String correction) {
        this.correction = correction;
    }

    public LocalDateTime getDateCorrection() {
        return dateCorrection;
    }

    public void setDateCorrection(LocalDateTime dateCorrection) {
        this.dateCorrection = dateCorrection;
    }

    public String getCorrecteurId() {
        return correcteurId;
    }

    public void setCorrecteurId(String correcteurId) {
        this.correcteurId = correcteurId;
    }

    public String getNomCorrecteur() {
        return nomCorrecteur;
    }

    public void setNomCorrecteur(String nomCorrecteur) {
        this.nomCorrecteur = nomCorrecteur;
    }

    public List<PieceJointe> getPiecesJointes() {
        return piecesJointes;
    }

    public void setPiecesJointes(List<PieceJointe> piecesJointes) {
        this.piecesJointes = piecesJointes != null ? piecesJointes : new ArrayList<>();
    }
}
