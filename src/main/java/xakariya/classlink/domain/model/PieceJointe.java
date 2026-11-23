package xakariya.classlink.domain.model;

import java.io.Serializable;

public class PieceJointe implements Serializable {
    private String id;
    private String nomOriginal;
    private String cheminStockage;
    private String typeMime;
    private Long tailleOctets;

    public PieceJointe() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNomOriginal() {
        return nomOriginal;
    }

    public void setNomOriginal(String nomOriginal) {
        this.nomOriginal = nomOriginal;
    }

    public String getCheminStockage() {
        return cheminStockage;
    }

    public void setCheminStockage(String cheminStockage) {
        this.cheminStockage = cheminStockage;
    }

    public String getTypeMime() {
        return typeMime;
    }

    public void setTypeMime(String typeMime) {
        this.typeMime = typeMime;
    }

    public Long getTailleOctets() {
        return tailleOctets;
    }

    public void setTailleOctets(Long tailleOctets) {
        this.tailleOctets = tailleOctets;
    }
}
