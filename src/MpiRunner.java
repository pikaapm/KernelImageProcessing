import mpi.MPI;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MpiRunner {
    public static void main(String[] args) {
        // Inicializacija MPI okolja
        String[] appArgs = MPI.Init(args);

        try {
            new MpiRunner().zazeni(appArgs);
        } finally {
            // Zaključek MPI okolja
            MPI.Finalize();
        }
    }

    private void zazeni(String[] appArgs) {
        SlikaDatoteke slikaDatoteke = new SlikaDatoteke();
        MpiImageProcessor mpiImageProcessor = new MpiImageProcessor();

        try {
            int rank = MPI.COMM_WORLD.Rank();

            if (appArgs.length < 2) {
                if (rank == 0) {
                    izpisiUporabo();
                }
                return;
            }

            File vhod = new File(appArgs[0]);
            List<OperacijaSlike> operacije = parseOperacije(appArgs[1]);
            List<File> slike = slikaDatoteke.najdiSlike(vhod);

            if (slike.isEmpty()) {
                if (rank == 0) {
                    System.out.println("Ni slik za MPI procesiranje: " + vhod.getAbsolutePath());
                }
                return;
            }

            for (File slikaDatoteka : slike) {
                BufferedImage input = slikaDatoteke.preberiSliko(slikaDatoteka);

                long start = System.nanoTime();
                BufferedImage output = mpiImageProcessor.procesiraj(input, operacije);
                long casMs = (System.nanoTime() - start) / 1_000_000;

                if (rank == 0) {
                    // Tukaj je popravljen klic (brez parametra operacije)
                    File outputDatoteka = slikaDatoteke.pripraviOutputDatoteko(slikaDatoteka);
                    ImageIO.write(output, slikaDatoteke.dobiFormatZaPisanje(outputDatoteka), outputDatoteka);
                    System.out.println("MPI cas za sliko: " + casMs
                            + " ms. Output: " + outputDatoteka.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            System.out.println("MPI napaka: " + e.getMessage());
            izpisiUporabo();
        }
    }

    private List<OperacijaSlike> parseOperacije(String tekst) {
        List<OperacijaSlike> operacije = new ArrayList<>();
        String[] deli = tekst.split(",");

        for (String del : deli) {
            String ime = del.trim().toLowerCase();

            if (ime.equals("mirror") || ime.equals("mirrored") || ime.equals("horizontalmirror")) {
                operacije.add(OperacijaSlike.MIRROR);
            } else if (ime.equals("blur")) {
                operacije.add(OperacijaSlike.BLUR);
            } else if (ime.equals("sharpen")) {
                operacije.add(OperacijaSlike.SHARPEN);
            } else if (ime.equals("edge") || ime.equals("edgedetection")) {
                operacije.add(OperacijaSlike.EDGE);
            }
        }

        if (operacije.isEmpty()) {
            throw new IllegalArgumentException("Nobena operacija ni prepoznana: " + tekst);
        }

        return operacije;
    }

    private void izpisiUporabo() {
        System.out.println("Zagon z MPJ Express:");
        System.out.println("mpjrun -np 4 -cp out MpiRunner \"samples/test_1000x1000.png\" mirror,blur");
    }
}