import javax.imageio.ImageIO;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AplikacijaGui {

    // Regex za iskanje časa v konzolnem izpisu novega MpiRunnerja
    private Pattern MPI_CAS_PATTERN = Pattern.compile("MPI cas za sliko: (\\d+) ms");

    private JFrame frame;
    private SlikaDatoteke slikaDatoteke = new SlikaDatoteke();
    private ImageProcessor imageProcessor = new ImageProcessor();
    private SlikaPanel originalPanel = new SlikaPanel("Original");
    private SlikaPanel rezultatPanel = new SlikaPanel("Result");
    private JTextField izbranaPotField = new JTextField("Ni izbrane slike ali mape");
    private JLabel statusLabel = new JLabel("Izberi sliko ali mapo in dodaj operacije.");
    private JLabel casLabel = new JLabel("Cas: /");
    private JComboBox<OperacijaSlike> operacijeCombo = new JComboBox<>(OperacijaSlike.values());
    private DefaultListModel<OperacijaSlike> operacijeModel = new DefaultListModel<>();
    private JList<OperacijaSlike> operacijeList = new JList<>(operacijeModel);

    private File izbranaPot;
    private BufferedImage originalSlika;
    private BufferedImage rezultatSlika;

    public AplikacijaGui() {
        frame = new JFrame("Kernel Image Procesiranje");
    }

    public void pokazi() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));
        frame.getRootPane().setBorder(new EmptyBorder(8, 8, 8, 8));
        frame.add(narediZgornjiPanel(), BorderLayout.NORTH);
        frame.add(narediSredino(), BorderLayout.CENTER);
        frame.add(narediSpodnjiPanel(), BorderLayout.SOUTH);
        frame.setSize(1100, 700);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel narediZgornjiPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel gumbi = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

        JButton izberiSlikoGumb = new JButton("Izberi sliko");
        izberiSlikoGumb.addActionListener(e -> izberiSliko());

        JButton izberiMapoGumb = new JButton("Izberi mapo");
        izberiMapoGumb.addActionListener(e -> izberiMapo());

        gumbi.add(izberiSlikoGumb);
        gumbi.add(izberiMapoGumb);

        izbranaPotField.setEditable(false);
        izbranaPotField.setFocusable(false);
        izbranaPotField.setColumns(45);

        JPanel potPanel = new JPanel(new BorderLayout(6, 0));
        potPanel.add(new JLabel("Izbrano:"), BorderLayout.WEST);
        potPanel.add(izbranaPotField, BorderLayout.CENTER);

        panel.add(gumbi, BorderLayout.WEST);
        panel.add(potPanel, BorderLayout.CENTER);
        return panel;
    }

    private JSplitPane narediSredino() {
        JPanel slikePanel = new JPanel(new GridLayout(1, 2, 8, 8));
        slikePanel.add(originalPanel);
        slikePanel.add(rezultatPanel);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, slikePanel, narediDesniPanel());
        split.setResizeWeight(0.78);
        split.setDividerLocation(820);
        return split;
    }

    private JPanel narediDesniPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel dodajPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton dodajGumb = new JButton("Dodaj");
        dodajGumb.addActionListener(e -> dodajOperacijo());
        dodajPanel.add(operacijeCombo);
        dodajPanel.add(dodajGumb);

        JScrollPane scroll = new JScrollPane(operacijeList);
        scroll.setPreferredSize(new Dimension(220, 260));

        JPanel seznamGumbi = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton odstraniGumb = new JButton("Odstrani");
        odstraniGumb.addActionListener(e -> odstraniOperacijo());
        JButton pocistiGumb = new JButton("Pocisti");
        pocistiGumb.addActionListener(e -> operacijeModel.clear());
        seznamGumbi.add(odstraniGumb);
        seznamGumbi.add(pocistiGumb);

        JPanel runPanel = new JPanel(new GridLayout(3, 1, 4, 4));
        JButton runSekvencno = new JButton("Run sekvencno");
        runSekvencno.addActionListener(e -> procesiraj(false));
        JButton runVecnitno = new JButton("Run vecnitno");
        runVecnitno.addActionListener(e -> procesiraj(true));
        JButton runMpi = new JButton("Run MPI");
        runMpi.addActionListener(e -> procesirajMpi());
        runPanel.add(runSekvencno);
        runPanel.add(runVecnitno);
        runPanel.add(runMpi);

        panel.add(dodajPanel, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel spodaj = new JPanel(new BorderLayout(8, 8)); //naredimo malo spacinga
        spodaj.add(seznamGumbi, BorderLayout.NORTH);
        spodaj.add(runPanel, BorderLayout.SOUTH);
        panel.add(spodaj, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel narediSpodnjiPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(4, 0, 0, 0));
        casLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        casLabel.setPreferredSize(new Dimension(420, 24));
        casLabel.setBorder(new EmptyBorder(0, 16, 0, 24));
        panel.add(statusLabel, BorderLayout.CENTER);
        panel.add(casLabel, BorderLayout.EAST);
        return panel;
    }

    private void izberiSliko() {
        JFileChooser chooser = new JFileChooser(dobiSamplesMapo());
        chooser.setDialogTitle("Izberi sliko");
        chooser.setFileFilter(new FileNameExtensionFilter("Slike", "jpg", "jpeg", "png", "bmp"));
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            izbranaPot = chooser.getSelectedFile();
            naloziPreviewSliko(izbranaPot);
        }
    }

    private void izberiMapo() {
        JFileChooser chooser = new JFileChooser(dobiSamplesMapo());
        chooser.setDialogTitle("Izberi mapo s slikami");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            izbranaPot = chooser.getSelectedFile();
            List<File> slike = slikaDatoteke.najdiSlike(izbranaPot);
            izbranaPotField.setText(izbranaPot.getAbsolutePath());
            rezultatPanel.setSlika(null);
            rezultatSlika = null;

            if (slike.isEmpty()) {
                originalPanel.setSlika(null);
                statusLabel.setText("V tej mapi nisem nasla slik. " + slikaDatoteke.podprtiFormatiText());
                return;
            }

            naloziPreviewSliko(slike.get(0));
            izbranaPotField.setText(izbranaPot.getAbsolutePath());
            statusLabel.setText("Mapa izbrana, za preview kazem prvo sliko. Skupaj slik: " + slike.size());
        }
    }

    private void naloziPreviewSliko(File datoteka) {
        try {
            originalSlika = slikaDatoteke.preberiSliko(datoteka);
            originalPanel.setSlika(originalSlika);
            rezultatPanel.setSlika(null);
            rezultatSlika = null;
            izbranaPotField.setText(datoteka.getAbsolutePath());
            statusLabel.setText("Slika nalozena: " + datoteka.getName());
            casLabel.setText("Cas: /");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Slike ne morem odpreti: " + e.getMessage());
        }
    }

    private void dodajOperacijo() {
        OperacijaSlike operacija = (OperacijaSlike) operacijeCombo.getSelectedItem();
        if (operacija != null) {
            operacijeModel.addElement(operacija);
        }
    }

    private void odstraniOperacijo() {
        int index = operacijeList.getSelectedIndex();
        if (index >= 0) {
            operacijeModel.remove(index);
        }
    }

    private void procesiraj(boolean vecnitno) {
        if (izbranaPot == null) {
            JOptionPane.showMessageDialog(frame, "Najprej izberi sliko ali mapo.");
            return;
        }

        List<OperacijaSlike> operacije = preberiOperacije();
        if (operacije.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Dodaj vsaj eno operacijo.");
            return;
        }

        List<File> slike = slikaDatoteke.najdiSlike(izbranaPot);
        if (slike.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Ni slik za procesirat.");
            return;
        }

        statusLabel.setText("Procesiram " + slike.size() + " slik...");
        casLabel.setText("Cas: delam...");

        SwingWorker<ProcessRezultat, Void> worker = new SwingWorker<>() {
            @Override
            protected ProcessRezultat doInBackground() throws Exception {
                long start = System.nanoTime();
                BufferedImage zadnjaSlika = null;
                File zadnjiOutput = null;

                for (File slikaDatoteka : slike) {
                    BufferedImage input = slikaDatoteke.preberiSliko(slikaDatoteka);
                    BufferedImage output;

                    if (vecnitno) {
                        output = imageProcessor.uporabiVecnitno(input, operacije);
                    } else {
                        output = imageProcessor.uporabiSekvencno(input, operacije);
                    }

                    // Tukaj je popravljen klic (brez parametra operacije)
                    File outputDatoteka = slikaDatoteke.pripraviOutputDatoteko(slikaDatoteka);
                    ImageIO.write(output, slikaDatoteke.dobiFormatZaPisanje(outputDatoteka), outputDatoteka);
                    zadnjaSlika = output;
                    zadnjiOutput = outputDatoteka;
                }

                long konec = System.nanoTime();
                return new ProcessRezultat(zadnjaSlika, zadnjiOutput, slike.size(), (konec - start) / 1_000_000);
            }

            @Override
            protected void done() {
                try {
                    ProcessRezultat rezultat = get();
                    rezultatSlika = rezultat.slika;
                    rezultatPanel.setSlika(rezultatSlika);
                    statusLabel.setText("Koncano. Slik: " + rezultat.steviloSlik
                            + ". Zadnji output: " + rezultat.outputDatoteka.getAbsolutePath());
                    casLabel.setText("Cas: " + rezultat.casMs + " ms (" + (vecnitno ? "vecnitno" : "sekvencno") + ")");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(frame, "Napaka pri procesiranju: " + e.getMessage());
                    statusLabel.setText("Procesiranje ni uspelo.");
                }
            }
        };

        worker.execute();
    }

    private void procesirajMpi() {
        if (izbranaPot == null) {
            JOptionPane.showMessageDialog(frame, "Najprej izberi sliko ali mapo.");
            return;
        }

        List<OperacijaSlike> operacije = preberiOperacije();
        if (operacije.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Dodaj vsaj eno operacijo.");
            return;
        }

        statusLabel.setText("Zaganjam sistemski MPI...");
        casLabel.setText("Cas: MPI obdelava...");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                int procesi = Math.max(2, Runtime.getRuntime().availableProcessors());
                List<String> ukaz = new ArrayList<>();

                // Preberemo MPJ_HOME sistemsko spremenljivko
                String mpjHome = System.getenv("MPJ_HOME");
                boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
                String mpjrunUkaz = windows ? "mpjrun.bat" : "mpjrun.sh";
                if (mpjHome != null && !mpjHome.isBlank()) {
                    mpjrunUkaz = mpjHome + (windows ? "\\bin\\mpjrun.bat" : "/bin/mpjrun.sh");
                }

                ukaz.add(mpjrunUkaz);
                ukaz.add("-np");
                ukaz.add(String.valueOf(procesi));
                ukaz.add("-cp");
                ukaz.add(System.getProperty("java.class.path"));
                ukaz.add("MpiRunner");
                ukaz.add(izbranaPot.getAbsolutePath());

                // Sestavimo operacije
                StringBuilder ops = new StringBuilder();
                for (int i = 0; i < operacije.size(); i++) {
                    if (i > 0) ops.append(",");
                    ops.append(operacije.get(i).getImeZaDatoteko());
                }
                ukaz.add(ops.toString());

                ProcessBuilder builder = new ProcessBuilder(ukaz);
                builder.directory(new File("."));
                builder.redirectErrorStream(true);
                Process process = builder.start();

                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append(System.lineSeparator());
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new Exception("Skripta se je koncala z napako " + exitCode + ":\n" + output.toString());
                }

                return output.toString();
            }

            @Override
            protected void done() {
                try {
                    String rezultat = get();
                    Matcher matcher = MPI_CAS_PATTERN.matcher(rezultat);
                    long skupniCas = 0;
                    boolean nasel = false;

                    while (matcher.find()) {
                        skupniCas += Long.parseLong(matcher.group(1));
                        nasel = true;
                    }

                    if (nasel) {
                        casLabel.setText("Cas: " + skupniCas + " ms (MPI)");
                        statusLabel.setText("MPI uspesno koncano. (Preveri output mapo)");
                    } else {
                        casLabel.setText("Cas: /");
                        statusLabel.setText("MPI koncano, a brez podanega casa v izpisu.");
                    }

                    // Za lazji pregled izpisemo se v navadno konzolo
                    System.out.println(rezultat);

                } catch (Exception e) {
                    statusLabel.setText("MPI ni uspel.");
                    JOptionPane.showMessageDialog(frame, "Napaka pri zagonu sistemskega MPI:\n" + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    private File dobiSamplesMapo() {
        File samplesMapa = new File("samples");
        if (samplesMapa.exists() && samplesMapa.isDirectory()) {
            return samplesMapa;
        }
        return new File(".");
    }

    private List<OperacijaSlike> preberiOperacije() {
        List<OperacijaSlike> operacije = new ArrayList<>();
        for (int i = 0; i < operacijeModel.size(); i++) {
            operacije.add(operacijeModel.get(i));
        }
        return operacije;
    }

    private class ProcessRezultat {
        BufferedImage slika;
        File outputDatoteka;
        int steviloSlik;
        long casMs;

        ProcessRezultat(BufferedImage slika, File outputDatoteka, int steviloSlik, long casMs) {
            this.slika = slika;
            this.outputDatoteka = outputDatoteka;
            this.steviloSlik = steviloSlik;
            this.casMs = casMs;
        }
    }

    private class SlikaPanel extends JPanel {
        private BufferedImage slika;
        private String tekst;

        SlikaPanel(String tekst) {
            this.tekst = tekst;
            setBackground(Color.WHITE);
        }

        void setSlika(BufferedImage slika) {
            this.slika = slika;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (slika == null) {
                g.setColor(Color.DARK_GRAY);
                g.drawString(tekst, 20, 25);
                return;
            }

            int panelW = getWidth();
            int panelH = getHeight();
            double faktor = Math.min((double) panelW / slika.getWidth(), (double) panelH / slika.getHeight());
            int novaSirina = Math.max(1, (int) (slika.getWidth() * faktor));
            int novaVisina = Math.max(1, (int) (slika.getHeight() * faktor));
            int x = (panelW - novaSirina) / 2;
            int y = (panelH - novaVisina) / 2;
            Image scaled = slika.getScaledInstance(novaSirina, novaVisina, Image.SCALE_SMOOTH);
            g.drawImage(scaled, x, y, this);
        }
    }
}