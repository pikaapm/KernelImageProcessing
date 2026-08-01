import mpi.MPI;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class MpiBenchmarkRunner {
    private int[] VELIKOSTI = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};

    public static void main(String[] args) {
        // Inicializacija MPI okolja
        MPI.Init(args);

        try {
            new MpiBenchmarkRunner().zazeni();
        } finally {
            // Zaključek MPI okolja
            MPI.Finalize();
        }
    }

    private void zazeni() {
        MpiImageProcessor mpiImageProcessor = new MpiImageProcessor();

        try {
            int rank = MPI.COMM_WORLD.Rank();
            int size = MPI.COMM_WORLD.Size();

            File samplesMapa = new File("samples");
            File outputMapa = new File("output");
            if (rank == 0) {
                samplesMapa.mkdirs();
                outputMapa.mkdirs();
            }

            // Počakamo, da rank 0 ustvari mape, preden nadaljujemo
            MPI.COMM_WORLD.Barrier();

            List<OperacijaSlike> operacije = Arrays.asList(
                    OperacijaSlike.MIRROR,
                    OperacijaSlike.BLUR,
                    OperacijaSlike.SHARPEN,
                    OperacijaSlike.EDGE
            );

            File csv = new File(outputMapa, "report_mpi.csv");
            PrintWriter writer = null;
            if (rank == 0) {
                writer = new PrintWriter(csv);
                writer.println("image,width,height,operations,mode,processes,runtime_ms");
            }

            for (int velikost : VELIKOSTI) {
                File testSlika = new File(samplesMapa, "test_" + velikost + "x" + velikost + ".png");

                // Samo glavni proces generira in shranjuje slike
                if (rank == 0) {
                    narediTestSlikoCeManjka(testSlika, velikost, velikost);
                }

                // Počakamo, da proces 0 sliko zapiše na disk, preden jo vsi preberejo
                MPI.COMM_WORLD.Barrier();

                BufferedImage input = ImageIO.read(testSlika);
                long start = System.nanoTime();

                mpiImageProcessor.procesiraj(input, operacije);

                long casMs = (System.nanoTime() - start) / 1_000_000;

                if (rank == 0) {
                    writer.println(testSlika.getName() + "," + velikost + "," + velikost + ","
                            + operacijeText(operacije) + ",mpi," + size + "," + casMs);
                    System.out.println(testSlika.getName() + " MPI: " + casMs + " ms");
                }
            }

            if (writer != null) {
                writer.close();
                System.out.println("MPI benchmark report: " + csv.getAbsolutePath());
            }
        } catch (Exception e) {
            System.out.println("MPI benchmark napaka: " + e.getMessage());
            System.out.println("Pravilen zagon: mpjrun -np 4 -cp out MpiBenchmarkRunner");
        }
    }

    private void narediTestSlikoCeManjka(File datoteka, int sirina, int visina) throws Exception {
        if (datoteka.exists()) {
            return;
        }

        BufferedImage slika = new BufferedImage(sirina, visina, BufferedImage.TYPE_INT_RGB);

        // Osnovno generiranje naključij brez fiksnega semena (slika bo ob vsakem zagonu drugačna)
        Random random = new Random();

        for (int y = 0; y < visina; y++) {
            for (int x = 0; x < sirina; x++) {
                int r = (x * 255 / Math.max(1, sirina - 1) + random.nextInt(40)) % 256;
                int g = (y * 255 / Math.max(1, visina - 1) + random.nextInt(40)) % 256;
                int b = random.nextInt(256);
                slika.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        ImageIO.write(slika, "png", datoteka);
    }

    private String operacijeText(List<OperacijaSlike> operacije) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < operacije.size(); i++) {
            if (i > 0) {
                builder.append("+");
            }
            builder.append(operacije.get(i).getImeZaDatoteko());
        }
        return builder.toString();
    }
}