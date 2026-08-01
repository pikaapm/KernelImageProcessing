import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SlikaDatoteke {
    public List<File> najdiSlike(File izbranaPot) {
        List<File> slike = new ArrayList<>();

        if (izbranaPot == null) {
            return slike;
        }

        if (izbranaPot.isFile() && jeSlika(izbranaPot)) {
            slike.add(izbranaPot);
            return slike;
        }

        // Ce izberemo mapo, vzamemo samo slike direktno v tej mapi.
        // Ne gremo rekurzivno, ker bi se hitro nabrala cela zmeda podmap.
        File[] datoteke = izbranaPot.listFiles();
        if (datoteke == null) {
            return slike;
        }

        for (File datoteka : datoteke) {
            if (datoteka.isFile() && jeSlika(datoteka)) {
                slike.add(datoteka);
            }
        }

        return slike;
    }

    public File pripraviOutputDatoteko(File inputDatoteka) {
        File outputMapa = new File("output");
        if (!outputMapa.exists()) {
            outputMapa.mkdirs();
        }
        // Preprosto vrne datoteko z istim imenom v mapo output
        return new File(outputMapa, inputDatoteka.getName());
    }

    public String dobiFormatZaPisanje(File outputDatoteka) {
        String koncnica = dobiKoncnico(outputDatoteka.getName());
        if (koncnica.equals("jpg") || koncnica.equals("jpeg") || koncnica.equals("png") || koncnica.equals("bmp")) {
            return koncnica;
        }
        return "png";
    }

    public String podprtiFormatiText() {
        return "Podprti formati: " + String.join(", ", ImageIO.getReaderFileSuffixes());
    }

    public BufferedImage preberiSliko(File datoteka) throws IOException {
        BufferedImage slika = ImageIO.read(datoteka);
        if (slika == null) {
            throw new IOException("Datoteka ni podprta slika.");
        }

        return slika;
    }

    private boolean jeSlika(File datoteka) {
        String koncnica = dobiKoncnico(datoteka.getName());
        return koncnica.equals("jpg")
                || koncnica.equals("jpeg")
                || koncnica.equals("png")
                || koncnica.equals("bmp");
    }

    private String dobiKoncnico(String ime) {
        int pika = ime.lastIndexOf('.');
        if (pika < 0 || pika == ime.length() - 1) {
            return "";
        }
        return ime.substring(pika + 1).toLowerCase();
    }
}