package xakariya.classlink.domain.model;

import xakariya.classlink.domain.enums.TypeNotification;

import java.io.Serializable;

public class PreferenceNotification implements Serializable {
    private String id;
    private String utilisateurId;
    private TypeNotification type;
    private Boolean dansApplication;
    private Boolean parEmail;

    public PreferenceNotification() {
        this.dansApplication = true;
        this.parEmail = true;
    }

    public PreferenceNotification(String id, String utilisateurId, TypeNotification type,
                                  Boolean dansApplication, Boolean parEmail) {
        this.id = id;
        this.utilisateurId = utilisateurId;
        this.type = type;
        this.dansApplication = dansApplication != null ? dansApplication : true;
        this.parEmail = parEmail != null ? parEmail : true;
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

    public TypeNotification getType() {
        return type;
    }

    public void setType(TypeNotification type) {
        this.type = type;
    }

    public Boolean getDansApplication() {
        return dansApplication;
    }

    public void setDansApplication(Boolean dansApplication) {
        this.dansApplication = dansApplication;
    }

    public Boolean getParEmail() {
        return parEmail;
    }

    public void setParEmail(Boolean parEmail) {
        this.parEmail = parEmail;
    }
}
