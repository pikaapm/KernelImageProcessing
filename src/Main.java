import java.io.File;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        File samplesMapa = new File("samples");
        File outputMapa = new File("output");
        samplesMapa.mkdirs();
        outputMapa.mkdirs();

        SwingUtilities.invokeLater(() -> new AplikacijaGui().pokazi());
    }
}