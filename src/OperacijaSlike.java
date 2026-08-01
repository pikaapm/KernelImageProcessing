//tu so definirani enumi/zadeve, ki jih dovolimo delati na slikah
public enum OperacijaSlike {
    MIRROR("Horizontal mirror", "mirrored"),
    BLUR("Blur", "blur"),
    SHARPEN("Sharpen", "sharpen"),
    EDGE("Edge detection", "edge");

    private String prikaz;
    private String imeZaDatoteko;

    OperacijaSlike(String prikaz, String imeZaDatoteko) {
        this.prikaz = prikaz;
        this.imeZaDatoteko = imeZaDatoteko;
    }

    public String getImeZaDatoteko() {
        return imeZaDatoteko;
    }

    @Override
    public String toString() {
        return prikaz;
    }
}
