import mpi.MPI;
import java.awt.image.BufferedImage;
import java.util.List;

public class MpiImageProcessor {
    private int TAG_CHUNK = 501;
    private int TAG_FULL_IMAGE = 502;

    private double[][] BLUR_KERNEL = {
            {1.0 / 9, 1.0 / 9, 1.0 / 9},
            {1.0 / 9, 1.0 / 9, 1.0 / 9},
            {1.0 / 9, 1.0 / 9, 1.0 / 9}
    };

    private double[][] SHARPEN_KERNEL = {
            {0, -1, 0},
            {-1, 5, -1},
            {0, -1, 0}
    };

    private double[][] EDGE_KERNEL = {
            {-1, -1, -1},
            {-1, 8, -1},
            {-1, -1, -1}
    };

    public BufferedImage procesiraj(BufferedImage input, List<OperacijaSlike> operacije)
            throws Exception {
        int sirina = input.getWidth();
        int visina = input.getHeight();
        int[] trenutniPixli = new int[sirina * visina];

        // Pri MPI je lazje posiljat navaden int array kot cel BufferedImage objekt.
        // Vsak int je en RGB pixel, zato sliko najprej "sploscimo".
        input.getRGB(0, 0, sirina, visina, trenutniPixli, 0, sirina);

        int[] rezultat = procesirajPixle(trenutniPixli, sirina, visina, operacije);

        if (MPI.COMM_WORLD.Rank() != 0) {
            return null;
        }

        BufferedImage output = new BufferedImage(sirina, visina, BufferedImage.TYPE_INT_RGB);
        output.setRGB(0, 0, sirina, visina, rezultat, 0, sirina);
        return output;
    }

    private int[] procesirajPixle(int[] trenutniPixli, int sirina, int visina,
                                  List<OperacijaSlike> operacije) throws Exception {
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        for (OperacijaSlike operacija : operacije) {
            // Rank pove kateri proces trenutno dela. Vsak proces dobi svoj pas vrstic od slike.
            int startY = rank * visina / size;
            int endY = (rank + 1) * visina / size;
            int[] lokalniPixli = new int[(endY - startY) * sirina];

            if (operacija == OperacijaSlike.MIRROR) {
                narediMirrorChunk(trenutniPixli, lokalniPixli, sirina, startY, endY);
            } else if (operacija == OperacijaSlike.EDGE) {
                narediEdgeChunk(trenutniPixli, lokalniPixli, sirina, visina, startY, endY);
            } else {
                narediConvolutionChunk(trenutniPixli, lokalniPixli, sirina, visina, startY, endY,
                        kernelZaOperacijo(operacija));
            }

            // Vsak proces naredi svoj kos slike, root pa kose zlepi in potem celo sliko poslje nazaj vsem.
            trenutniPixli = zberiInRazposljiSliko(lokalniPixli, sirina, visina, startY, endY);
        }

        return trenutniPixli;
    }

    private void narediMirrorChunk(int[] input, int[] output, int sirina, int startY, int endY) {
        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < sirina; x++) {
                output[(y - startY) * sirina + x] = input[y * sirina + (sirina - 1 - x)];
            }
        }
    }

    private void narediConvolutionChunk(int[] input, int[] output, int sirina, int visina,
                                        int startY, int endY, double[][] kernel) {
        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < sirina; x++) {
                if (x == 0 || y == 0 || x == sirina - 1 || y == visina - 1) {
                    output[(y - startY) * sirina + x] = input[y * sirina + x];
                } else {
                    output[(y - startY) * sirina + x] = izracunajPixel(input, sirina, x, y, kernel);
                }
            }
        }
    }

    private void narediEdgeChunk(int[] input, int[] output, int sirina, int visina, int startY, int endY) {
        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < sirina; x++) {
                if (x == 0 || y == 0 || x == sirina - 1 || y == visina - 1) {
                    output[(y - startY) * sirina + x] = 0;
                } else {
                    output[(y - startY) * sirina + x] = izracunajEdgePixel(input, sirina, x, y);
                }
            }
        }
    }

    private int[] zberiInRazposljiSliko(int[] lokalniPixli, int sirina, int visina,
                                        int startY, int endY) throws Exception {
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
        int[] celaSlika;

        if (rank == 0) {
            // Proces 0 je sef: pobere vse kose, jih nalepi nazaj v celo sliko,
            // potem pa celo sliko poslje vsem, da lahko naslednji filter nadaljuje iz istega stanja.
            celaSlika = new int[sirina * visina];
            System.arraycopy(lokalniPixli, 0, celaSlika, startY * sirina, lokalniPixli.length);

            for (int proces = 1; proces < size; proces++) {
                int procesStartY = proces * visina / size;
                int procesEndY = (proces + 1) * visina / size;
                int dolzina = (procesEndY - procesStartY) * sirina;

                if (dolzina > 0) {
                    int[] kos = new int[dolzina];
                    MPI.COMM_WORLD.Recv(kos, 0, dolzina, MPI.INT, proces, TAG_CHUNK);
                    System.arraycopy(kos, 0, celaSlika, procesStartY * sirina, dolzina);
                }
            }

            for (int proces = 1; proces < size; proces++) {
                MPI.COMM_WORLD.Send(celaSlika, 0, celaSlika.length, MPI.INT, proces, TAG_FULL_IMAGE);
            }
        } else {
            int dolzina = (endY - startY) * sirina;
            if (dolzina > 0) {
                // Worker proces poslje samo svoj kos slike root procesu.
                MPI.COMM_WORLD.Send(lokalniPixli, 0, dolzina, MPI.INT, 0, TAG_CHUNK);
            }

            celaSlika = new int[sirina * visina];
            MPI.COMM_WORLD.Recv(celaSlika, 0, celaSlika.length, MPI.INT, 0, TAG_FULL_IMAGE);
        }

        return celaSlika;
    }

    private int izracunajPixel(int[] input, int sirina, int centerX, int centerY, double[][] kernel) {
        double r = 0;
        double g = 0;
        double b = 0;

        for (int ky = -1; ky <= 1; ky++) {
            for (int kx = -1; kx <= 1; kx++) {
                int rgb = input[(centerY + ky) * sirina + (centerX + kx)];
                double faktor = kernel[ky + 1][kx + 1];
                r += ((rgb >> 16) & 0xff) * faktor;
                g += ((rgb >> 8) & 0xff) * faktor;
                b += (rgb & 0xff) * faktor;
            }
        }

        int rr = omejiBarvo((int) Math.round(r));
        int gg = omejiBarvo((int) Math.round(g));
        int bb = omejiBarvo((int) Math.round(b));
        return (rr << 16) | (gg << 8) | bb;
    }

    private int izracunajEdgePixel(int[] input, int sirina, int centerX, int centerY) {
        double sum = 0;

        for (int ky = -1; ky <= 1; ky++) {
            for (int kx = -1; kx <= 1; kx++) {
                int rgb = input[(centerY + ky) * sirina + (centerX + kx)];
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int gray = (r + g + b) / 3;
                double faktor = EDGE_KERNEL[ky + 1][kx + 1];

                sum += gray * faktor;
            }
        }

        int v = omejiBarvo((int) Math.round(Math.abs(sum)));
        return (v << 16) | (v << 8) | v;
    }

    private double[][] kernelZaOperacijo(OperacijaSlike operacija) {
        if (operacija == OperacijaSlike.BLUR) {
            return BLUR_KERNEL;
        }
        if (operacija == OperacijaSlike.SHARPEN) {
            return SHARPEN_KERNEL;
        }
        throw new IllegalArgumentException("Neznana kernel operacija: " + operacija);
    }

    private int omejiBarvo(int vrednost) {
        if (vrednost < 0) {
            return 0;
        }
        if (vrednost > 255) {
            return 255;
        }
        return vrednost;
    }
}