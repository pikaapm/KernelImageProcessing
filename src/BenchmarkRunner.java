import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class BenchmarkRunner {
    private int[] VELIKOSTI = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};

    public static void main(String[] args) throws Exception {
        new BenchmarkRunner().zazeni();
    }

    private void zazeni() throws Exception {
        File samplesMapa = new File("samples");
        File outputMapa = new File("output");
        samplesMapa.mkdirs();
        outputMapa.mkdirs();

        List<OperacijaSlike> operacije = Arrays.asList(
                OperacijaSlike.MIRROR,
                OperacijaSlike.BLUR,
                OperacijaSlike.SHARPEN,
                OperacijaSlike.EDGE
        );

        File csv = new File(outputMapa, "report.csv");
        File txt = new File(outputMapa, "report.txt");

        try (PrintWriter csvWriter = new PrintWriter(csv);
             PrintWriter txtWriter = new PrintWriter(txt)) {

            csvWriter.println("image,width,height,operations,mode,runtime_ms");
            txtWriter.println("Kernel Image Processing benchmark");
            txtWriter.println("CPU niti: " + Runtime.getRuntime().availableProcessors());
            txtWriter.println("Operacije: " + operacijeText(operacije));
            txtWriter.println();

            for (int velikost : VELIKOSTI) {
                File testSlika = new File(samplesMapa, "test_" + velikost + "x" + velikost + ".png");
                narediTestSlikoCeManjka(testSlika, velikost, velikost);
                BufferedImage input = ImageIO.read(testSlika);

                ImageProcessor processor = new ImageProcessor();
                long sekvencnoMs = izmeri(() -> processor.uporabiSekvencno(input, operacije));
                csvWriter.println(testSlika.getName() + "," + velikost + "," + velikost + ","
                        + operacijeText(operacije) + ",sekvencno," + sekvencnoMs);
                txtWriter.println(testSlika.getName() + " sekvencno: " + sekvencnoMs + " ms");

                long vecnitnoMs = izmeri(() -> processor.uporabiVecnitno(input, operacije));
                csvWriter.println(testSlika.getName() + "," + velikost + "," + velikost + ","
                        + operacijeText(operacije) + ",vecnitno," + vecnitnoMs);
                txtWriter.println(testSlika.getName() + " vecnitno: " + vecnitnoMs + " ms");
                txtWriter.println();
            }
        }

        System.out.println("Benchmark koncan.");
        System.out.println("CSV report: " + csv.getAbsolutePath());
        System.out.println("TXT report: " + txt.getAbsolutePath());
    }

    private long izmeri(Naloga naloga) {
        long start = System.nanoTime();
        BufferedImage rezultat = naloga.run();
        // Malo uporabimo rezultat, da Java ne more samo pametovat, da rezultat ni pomemben.
        rezultat.getRGB(rezultat.getWidth() / 2, rezultat.getHeight() / 2);
        return (System.nanoTime() - start) / 1_000_000;
    }

    private void narediTestSlikoCeManjka(File datoteka, int sirina, int visina) throws Exception {
        if (datoteka.exists()) {
            return;
        }

        BufferedImage slika = new BufferedImage(sirina, visina, BufferedImage.TYPE_INT_RGB); //TYPE_INT_RGB je RGB pixel brez prosojnosti
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

    private interface Naloga {
        BufferedImage run();
    }
}
