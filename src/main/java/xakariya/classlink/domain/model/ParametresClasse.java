package xakariya.classlink.domain.model;

import xakariya.classlink.domain.enums.PermissionCommentaire;
import xakariya.classlink.domain.enums.PermissionPublication;

import java.io.Serializable;

public class ParametresClasse implements Serializable {
    private String id;
    private PermissionPublication publicationAnnonces;
    private PermissionCommentaire commentaires;
    private Boolean codeAccesActif;

    public ParametresClasse() {
        this.publicationAnnonces = PermissionPublication.TOUS_LES_MEMBRES;
        this.commentaires = PermissionCommentaire.TOUS_LES_MEMBRES;
        this.codeAccesActif = true;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public PermissionPublication getPublicationAnnonces() {
        return publicationAnnonces;
    }

    public void setPublicationAnnonces(PermissionPublication publicationAnnonces) {
        this.publicationAnnonces = publicationAnnonces;
    }

    public PermissionCommentaire getCommentaires() {
        return commentaires;
    }

    public void setCommentaires(PermissionCommentaire commentaires) {
        this.commentaires = commentaires;
    }

    public Boolean getCodeAccesActif() {
        return codeAccesActif;
    }

    public void setCodeAccesActif(Boolean codeAccesActif) {
        this.codeAccesActif = codeAccesActif;
    }
}
