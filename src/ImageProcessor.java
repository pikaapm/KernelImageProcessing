import java.awt.image.BufferedImage;
import java.util.List;
import java.awt.Color;

public class ImageProcessor {
    public BufferedImage uporabiSekvencno(BufferedImage zacetnaSlika, List<OperacijaSlike> operacije) {
        BufferedImage trenutna = narediRgbKopijo(zacetnaSlika);

        // Operacije naredimo po vrsti, kot jih dodamo v GUI listi.
        // Vsak step dobi rezultat prejsnjega stepa, tako da se efekti lepo chainajo.
        for (OperacijaSlike operacija : operacije) {
            if (operacija == OperacijaSlike.MIRROR) {
                trenutna = mirrorHorizontal(trenutna);
            } else if (operacija == OperacijaSlike.EDGE) {
                trenutna = edgeSekvencno(trenutna);
            } else {
                trenutna = convolutionSekvencno(trenutna, kernelZaOperacijo(operacija));
            }
        }

        return trenutna;
    }

    public BufferedImage uporabiVecnitno(BufferedImage zacetnaSlika, List<OperacijaSlike> operacije) {
        BufferedImage trenutna = narediRgbKopijo(zacetnaSlika);

        // Ista logika kot sekvencno, samo posamezen filter razrezemo po vrsticah.
        // Tako lahko Java uporabi vec CPU niti, ce jih masina ima.
        for (OperacijaSlike operacija : operacije) {
            if (operacija == OperacijaSlike.MIRROR) {
                trenutna = mirrorHorizontalVecnitno(trenutna);
            } else if (operacija == OperacijaSlike.EDGE) {
                trenutna = edgeVecnitno(trenutna);
            } else {
                trenutna = convolutionVecnitno(trenutna, kernelZaOperacijo(operacija));
            }
        }

        return trenutna;
    }

    public BufferedImage narediRgbKopijo(BufferedImage original) {
        BufferedImage kopija = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);

        // Slike pretvorim v navaden RGB tip, da kasneje ni presenecenj z prozornostjo (alpha kanalom) ali cudnimi tipi slik.
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                kopija.setRGB(x, y, original.getRGB(x, y));
            }
        }

        return kopija;
    }

    private BufferedImage mirrorHorizontal(BufferedImage slika) {
        int sirina = slika.getWidth();
        int visina = slika.getHeight();
        BufferedImage rezultat = new BufferedImage(sirina, visina, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < visina; y++) {
            for (int x = 0; x < sirina; x++) {
                rezultat.setRGB(sirina - 1 - x, y, slika.getRGB(x, y));
            }
        }

        return rezultat;
    }

    private BufferedImage mirrorHorizontalVecnitno(BufferedImage slika) {
        int sirina = slika.getWidth();
        int visina = slika.getHeight();
        BufferedImage rezultat = new BufferedImage(sirina, visina, BufferedImage.TYPE_INT_RGB);
        int stNiti = dobiSteviloNiti(visina);
        Thread[] niti = new Thread[stNiti];

        // Vsaka nit dobi svoj vodoravni 'pas' vrstic. Ne piseta v iste pixle, zato tukaj ne rabim lockov.
        //Npr za 100 vrstic in 4 niti je potem
        //nit 0: y = 0  do 24
        //nit 1: y = 25 do 49
        //nit 2: y = 50 do 74
        //nit 3: y = 75 do 99
        for (int i = 0; i < stNiti; i++) {
            int startY = i * visina / stNiti;
            int endY = (i + 1) * visina / stNiti;

            niti[i] = new Thread(() -> {
                for (int y = startY; y < endY; y++) {
                    for (int x = 0; x < sirina; x++) {
                        rezultat.setRGB(sirina - 1 - x, y, slika.getRGB(x, y));
                    }
                }
            });
            niti[i].start();
        }

        pocakajNiti(niti);
        return rezultat;
    }

    private BufferedImage convolutionSekvencno(BufferedImage slika, double[][] kernel) {
        int sirina = slika.getWidth();
        int visina = slika.getHeight();
        BufferedImage rezultat = narediRgbKopijo(slika);

        // Rob pustimo isti kot original, ker je to najbolj preprost način in ne potrebujemo dodajati paddinga sliki.
        for (int y = 1; y < visina - 1; y++) {
            for (int x = 1; x < sirina - 1; x++) {
                rezultat.setRGB(x, y, izracunajPixel(slika, kernel, x, y));
            }
        }

        return rezultat;
    }

    private BufferedImage convolutionVecnitno(BufferedImage slika, double[][] kernel) {
        int sirina = slika.getWidth();
        int visina = slika.getHeight();
        BufferedImage rezultat = narediRgbKopijo(slika);

        if (sirina < 3 || visina < 3) {
            return rezultat;
        }

        int sredinskeVrstice = visina - 2;
        int stNiti = dobiSteviloNiti(sredinskeVrstice);
        Thread[] niti = new Thread[stNiti];

        // Tudi convolution razdelimo po vrsticah. Robov ne racunamo, ker jih pustimo take kot so (bolj preprosto).
        for (int i = 0; i < stNiti; i++) {
            int startY = 1 + i * sredinskeVrstice / stNiti;
            int endY = 1 + (i + 1) * sredinskeVrstice / stNiti;

            niti[i] = new Thread(() -> {
                for (int y = startY; y < endY; y++) {
                    for (int x = 1; x < sirina - 1; x++) {
                        rezultat.setRGB(x, y, izracunajPixel(slika, kernel, x, y));
                    }
                }
            });
            niti[i].start();
        }

        pocakajNiti(niti);
        return rezultat;
    }

    private BufferedImage edgeSekvencno(BufferedImage slika) {
        int sirina = slika.getWidth();
        int visina = slika.getHeight();
        BufferedImage rezultat = new BufferedImage(sirina, visina, BufferedImage.TYPE_INT_RGB);
        double[][] edgeKernel = {
                {-1, -1, -1},
                {-1, 8, -1},
                {-1, -1, -1}
        };

        // Pri edge detection hocemo crn rob, ne originalnega okvirja.
        // Drugace dobim filtered sredino in normalen okvir, kar izgleda malo cudno vizualno.
        for (int y = 1; y < visina - 1; y++) {
            for (int x = 1; x < sirina - 1; x++) {
                rezultat.setRGB(x, y, izracunajEdgePixel(slika, edgeKernel, x, y));
            }
        }

        return rezultat;
    }

    private BufferedImage edgeVecnitno(BufferedImage slika) {
        int sirina = slika.getWidth();
        int visina = slika.getHeight();
        BufferedImage rezultat = new BufferedImage(sirina, visina, BufferedImage.TYPE_INT_RGB);

        if (sirina < 3 || visina < 3) {
            return rezultat;
        }

        int sredinskeVrstice = visina - 2;
        int stNiti = dobiSteviloNiti(sredinskeVrstice);
        Thread[] niti = new Thread[stNiti];
        double[][] edgeKernel = {
                {-1, -1, -1},
                {-1, 8, -1},
                {-1, -1, -1}
        };

        // Edge naredimo posebej, ker tukaj uporabimo grayscale + abs.
        // Tako se ne zgubi polovica roba, ko convolution vrne negativne vrednosti.
        for (int i = 0; i < stNiti; i++) {
            int startY = 1 + i * sredinskeVrstice / stNiti;
            int endY = 1 + (i + 1) * sredinskeVrstice / stNiti;

            niti[i] = new Thread(() -> {
                for (int y = startY; y < endY; y++) {
                    for (int x = 1; x < sirina - 1; x++) {
                        rezultat.setRGB(x, y, izracunajEdgePixel(slika, edgeKernel, x, y));
                    }
                }
            });
            niti[i].start();
        }

        pocakajNiti(niti);
        return rezultat;
    }

    private int izracunajPixel(BufferedImage slika, double[][] kernel, int centerX, int centerY) {
        //so doubli ker so kernel vrednosti lahko decimalke
        double r = 0;
        double g = 0;
        double b = 0;

        // To je 'naivna' convolution varianta: 3x3 okolica pixla krat kernel.
        // ky = -1 je ena vrstica nad
        // ky =  0 je trenutna vrstica
        // ky =  1 je ena vrstica pod
        for (int ky = -1; ky <= 1; ky++) {
            for (int kx = -1; kx <= 1; kx++) {

                //preberemo sosednji pixel
                int rgb = slika.getRGB(centerX + kx, centerY + ky);

                //dobimo katero kernel vrednost uzamemo
                double faktor = kernel[ky + 1][kx + 1];

                // Color objekt nam omogoča dostop do red/green/blue brez bit shiftov (bolj berljivo).
                Color barva = new Color(rgb);

                r += barva.getRed() * faktor;
                g += barva.getGreen() * faktor;
                b += barva.getBlue() * faktor;
            }
        }

        //zaokrožimo, da lahko shranimo nazaj kot integer (ker so barve od 0-255)
        int rr = omejiBarvo((int) Math.round(r));
        int gg = omejiBarvo((int) Math.round(g));
        int bb = omejiBarvo((int) Math.round(b));

        return new Color(rr, gg, bb).getRGB();
    }

    private int izracunajEdgePixel(BufferedImage slika, double[][] edgeKernel, int centerX, int centerY) {
        double sum = 0;

        for (int ky = -1; ky <= 1; ky++) {
            for (int kx = -1; kx <= 1; kx++) {

                // Preberemo barvo trenutnega sosednjega piksla.
                int rgb = slika.getRGB(centerX + kx, centerY + ky);

                // da dobimo rdečo, zeleno in modro komponento brez bit shiftov.
                Color barva = new Color(rgb);

                int r = barva.getRed();
                int g = barva.getGreen();
                int b = barva.getBlue();

                // Piksel pretvorimo v sivinsko vrednost.
                int gray = (r + g + b) / 3;

                // Vzamemo ustrezno vrednost iz edge detection kernela.
                double faktor = edgeKernel[ky + 1][kx + 1];

                sum += gray * faktor;
            }
        }

        // Edge detection lahko da negativne vrednosti,
        // zato uporabimo Math.abs().
        // Nato vrednost zaokrožimo in omejimo na interval 0–255.
        int v = omejiBarvo((int) Math.round(Math.abs(sum)));

        // Ker je rezultat sivinska slika, imajo R, G in B enako vrednost.
        // Namesto ročnega sestavljanja z bit shifti uporabimo Color.
        return new Color(v, v, v).getRGB();
    }

    private double[][] kernelZaOperacijo(OperacijaSlike operacija) {
        if (operacija == OperacijaSlike.BLUR) {
            return new double[][]{
                    {1.0 / 9, 1.0 / 9, 1.0 / 9},
                    {1.0 / 9, 1.0 / 9, 1.0 / 9},
                    {1.0 / 9, 1.0 / 9, 1.0 / 9}
            };
        }
        if (operacija == OperacijaSlike.SHARPEN) {
            return new double[][]{
                    {0, -1, 0},
                    {-1, 5, -1},
                    {0, -1, 0}
            };
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

    private int dobiSteviloNiti(int steviloVrstic) {
        int cpuNiti = Runtime.getRuntime().availableProcessors();
        // Ne zbuildam vec niti kot je vrstic, ker potem bi se samo prazne niti delale za brezveze.
        return Math.max(1, Math.min(cpuNiti, steviloVrstic));
    }

    private void pocakajNiti(Thread[] niti) {
        for (Thread nit : niti) {
            try {
                nit.join();
            } catch (InterruptedException e) {
                System.out.println("Ena nit se ni lepo koncala.");
            }
        }
    }
}
