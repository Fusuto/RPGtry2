package org.main.tools;

import org.main.engine.AssetLoader;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SoundDesignerTool extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(SoundDesignerTool.class.getName());

    private static final int SAMPLE_RATE = 44_100;
    private static final int DEFAULT_STEP_COUNT = 32;
    private static final int MAX_STEP_COUNT = 64;
    private static final int MIN_NOTE = 24;
    private static final int MAX_NOTE = 84;
    private static final Integer[] ACTIVE_STEP_OPTIONS = {8, 16, 24, 32, 64};
    private static final Path OUTPUT_FOLDER = AssetLoader.generatedSoundsFolder();

    private final LayerState[] layers = {
            new LayerState("Layer 1", false),
            new LayerState("Layer 2", true)
    };
    private final PitchGrid pitchGrid = new PitchGrid();

    private final JComboBox<Integer> activeStepsBox = new JComboBox<>(ACTIVE_STEP_OPTIONS);
    private final JComboBox<String> layerBox = new JComboBox<>(new String[]{"Layer 1", "Layer 2"});
    private final JPanel layerControlHost = new JPanel(new CardLayout());
    private final JSlider durationSlider = new JSlider(120, 2500, 650);
    private final JTextField fileNameField = new JTextField("new_sound", 16);
    private final JLabel statusLabel = new JLabel("Ready.");
    private final JToggleButton loopButton = new JToggleButton("Loop");
    private final JToggleButton normalizeButton = new JToggleButton("Normalize", true);

    private SourceDataLine previewLine;
    private Thread previewThread;
    private volatile boolean previewPlaying = false;
    private int activeStepCount = DEFAULT_STEP_COUNT;
    private int selectedLayerIndex = 0;

    public SoundDesignerTool() {
        super("Sound Designer");

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(920, 600));

        activeStepsBox.setSelectedItem(DEFAULT_STEP_COUNT);
        initializeLayers();

        add(createControlPanel(), BorderLayout.NORTH);
        add(pitchGrid, BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.add(createGlobalToolbar(), BorderLayout.NORTH);
        controlPanel.add(createLayerToolbar(), BorderLayout.SOUTH);
        return controlPanel;
    }

    private JPanel createGlobalToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        toolbar.setBackground(new Color(95, 88, 79));

        JButton playButton = new JButton("Play");
        playButton.addActionListener(event -> playPreview());

        JButton stopButton = new JButton("Stop");
        stopButton.addActionListener(event -> stopPreview());

        JButton saveButton = new JButton("Save WAV");
        saveButton.addActionListener(event -> saveWav());

        activeStepsBox.addActionListener(event -> updateActiveStepCount());

        toolbar.add(new JLabel("Steps"));
        toolbar.add(activeStepsBox);
        toolbar.add(new JLabel("Base Len"));
        toolbar.add(durationSlider);
        toolbar.add(playButton);
        toolbar.add(stopButton);
        toolbar.add(loopButton);
        toolbar.add(normalizeButton);
        toolbar.add(new JLabel("Name"));
        toolbar.add(fileNameField);
        toolbar.add(saveButton);

        return toolbar;
    }

    private JPanel createLayerToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(new Color(80, 74, 67));

        JPanel selectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        selectorPanel.setBackground(new Color(80, 74, 67));
        selectorPanel.add(new JLabel("Edit"));
        selectorPanel.add(layerBox);

        layerBox.addActionListener(event -> {
            selectedLayerIndex = Math.max(0, layerBox.getSelectedIndex());
            ((CardLayout) layerControlHost.getLayout()).show(layerControlHost, currentLayer().name);
            pitchGrid.clampSelectedStep();
            pitchGrid.repaint();
        });

        layerControlHost.setBackground(new Color(80, 74, 67));
        for (LayerState layer : layers) {
            layerControlHost.add(createLayerPanel(layer), layer.name);
        }

        toolbar.add(selectorPanel, BorderLayout.WEST);
        toolbar.add(layerControlHost, BorderLayout.CENTER);
        return toolbar;
    }

    private JPanel createLayerPanel(LayerState layer) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        panel.setBackground(new Color(80, 74, 67));

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(event -> {
            fillNotes(layer, 48);
            pitchGrid.repaint();
        });

        JButton copyButton = new JButton("Copy Other");
        copyButton.addActionListener(event -> {
            copyOtherLayerTo(layer);
            pitchGrid.repaint();
        });

        JButton flattenButton = new JButton("Flatten");
        flattenButton.addActionListener(event -> {
            fillNotes(layer, 48);
            pitchGrid.repaint();
        });

        JButton rampButton = new JButton("Ramp");
        rampButton.addActionListener(event -> {
            rampNotes(layer);
            pitchGrid.repaint();
        });

        panel.add(new JLabel("Wave"));
        panel.add(layer.waveformBox);
        panel.add(new JLabel("Vol"));
        panel.add(layer.volumeSlider);
        panel.add(new JLabel("Atk"));
        panel.add(layer.attackSlider);
        panel.add(new JLabel("Rel"));
        panel.add(layer.releaseSlider);
        panel.add(layer.muteButton);
        panel.add(layer.soloButton);
        panel.add(clearButton);
        panel.add(copyButton);
        panel.add(flattenButton);
        panel.add(rampButton);

        return panel;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(new Color(255, 20, 80));
        footer.add(statusLabel, BorderLayout.WEST);
        return footer;
    }

    private void initializeLayers() {
        for (LayerState layer : layers) {
            initializeNotes(layer);
        }
    }

    private void initializeNotes(LayerState layer) {
        for (int i = 0; i < layer.notes.length; i++) {
            double arc = Math.sin((i / (double) (DEFAULT_STEP_COUNT - 1)) * Math.PI);
            layer.notes[i] = 36 + (int) Math.round(18 * arc);
        }
    }

    private void updateActiveStepCount() {
        Object selected = activeStepsBox.getSelectedItem();
        if (!(selected instanceof Integer newStepCount)) {
            return;
        }

        int oldStepCount = activeStepCount;
        activeStepCount = Math.max(1, Math.min(MAX_STEP_COUNT, newStepCount));

        if (activeStepCount > oldStepCount) {
            for (LayerState layer : layers) {
                int fillNote = layer.notes[Math.max(0, oldStepCount - 1)];
                for (int i = oldStepCount; i < activeStepCount; i++) {
                    layer.notes[i] = fillNote;
                }
            }
        }

        pitchGrid.clampSelectedStep();
        pitchGrid.repaint();
        status("Active steps set to " + activeStepCount + ".");
    }

    private void fillNotes(LayerState layer, int note) {
        for (int i = 0; i < activeStepCount; i++) {
            layer.notes[i] = note;
        }
    }

    private void rampNotes(LayerState layer) {
        for (int i = 0; i < activeStepCount; i++) {
            layer.notes[i] = 36 + i;
        }
    }

    private void copyOtherLayerTo(LayerState target) {
        LayerState source = layers[0] == target ? layers[1] : layers[0];

        for (int i = 0; i < MAX_STEP_COUNT; i++) {
            target.notes[i] = source.notes[i];
        }

        target.waveformBox.setSelectedItem(source.waveformBox.getSelectedItem());
        target.volumeSlider.setValue(source.volumeSlider.getValue());
        target.attackSlider.setValue(source.attackSlider.getValue());
        target.releaseSlider.setValue(source.releaseSlider.getValue());
    }

    private LayerState currentLayer() {
        return layers[Math.max(0, Math.min(layers.length - 1, selectedLayerIndex))];
    }

    private void playPreview() {
        stopPreview();

        byte[] audioBytes = renderAudioBytes();
        AudioFormat format = createAudioFormat();

        previewPlaying = true;
        previewThread = new Thread(() -> streamPreview(format, audioBytes), "sound-preview");
        previewThread.setDaemon(true);
        previewThread.start();
    }

    private void stopPreview() {
        previewPlaying = false;

        if (previewLine != null) {
            previewLine.stop();
            previewLine.close();
            previewLine = null;
        }

        status("Stopped.");
    }

    private void streamPreview(AudioFormat format, byte[] audioBytes) {
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            previewLine = (SourceDataLine) AudioSystem.getLine(info);
            previewLine.open(format);
            previewLine.start();

            status("Playing preview.");

            int chunkSize = 2048;

            do {
                int offset = 0;

                while (previewPlaying && offset < audioBytes.length) {
                    int bytesToWrite = Math.min(chunkSize, audioBytes.length - offset);
                    offset += previewLine.write(audioBytes, offset, bytesToWrite);
                }
            } while (previewPlaying && loopButton.isSelected());

            if (previewPlaying) {
                previewLine.drain();
            }

            previewLine.stop();
            previewLine.close();
            previewLine = null;

            status("Preview finished.");
        } catch (Exception e) {
            status("Preview failed: " + e.getMessage());
            LOGGER.log(Level.WARNING, "Preview failed.", e);
        } finally {
            previewPlaying = false;
        }
    }

    private void saveWav() {
        byte[] audioBytes = renderAudioBytes();
        AudioFormat format = createAudioFormat();
        String fileName = sanitizeFileName(fileNameField.getText());

        if (fileName.isBlank()) {
            fileName = "new_sound";
        }

        Path outputPath = OUTPUT_FOLDER.resolve(fileName + ".wav");

        try {
            Files.createDirectories(OUTPUT_FOLDER);

            AudioInputStream audioInputStream = new AudioInputStream(
                    new ByteArrayInputStream(audioBytes),
                    format,
                    audioBytes.length / format.getFrameSize()
            );

            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputPath.toFile());
            statusLabel.setText("Saved " + outputPath);
        } catch (Exception e) {
            statusLabel.setText("Save failed: " + e.getMessage());
            LOGGER.log(Level.WARNING, "Save failed.", e);
        }
    }

    private void status(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    private byte[] renderAudioBytes() {
        int durationMs = Math.max(1, durationSlider.getValue() * activeStepCount / DEFAULT_STEP_COUNT);
        int sampleCount = Math.max(1, SAMPLE_RATE * durationMs / 1000);
        double[] mixedSamples = new double[sampleCount];
        List<LayerState> audibleLayers = audibleLayers();

        for (LayerState layer : audibleLayers) {
            renderLayer(layer, mixedSamples);
        }

        double scale = outputScale(mixedSamples);
        byte[] audioBytes = new byte[sampleCount * 2];

        for (int sample = 0; sample < sampleCount; sample++) {
            double value = Math.max(-1.0, Math.min(1.0, mixedSamples[sample] * scale));
            short pcm = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value * Short.MAX_VALUE));

            int byteIndex = sample * 2;
            audioBytes[byteIndex] = (byte) (pcm & 0xff);
            audioBytes[byteIndex + 1] = (byte) ((pcm >> 8) & 0xff);
        }

        return audioBytes;
    }

    private void renderLayer(LayerState layer, double[] mixedSamples) {
        Waveform waveform = (Waveform) layer.waveformBox.getSelectedItem();
        if (waveform == null) {
            waveform = Waveform.SINE;
        }

        double volume = layer.volumeSlider.getValue() / 100.0;
        double phase = 0.0;

        for (int sample = 0; sample < mixedSamples.length; sample++) {
            double progress = sample / (double) mixedSamples.length;
            int step = Math.min(activeStepCount - 1, (int) Math.floor(progress * activeStepCount));
            double frequency = noteToFrequency(layer.notes[step]);
            phase += frequency / SAMPLE_RATE;
            phase -= Math.floor(phase);

            mixedSamples[sample] += waveform.sample(phase) * envelope(layer, sample, mixedSamples.length) * volume;
        }
    }

    private List<LayerState> audibleLayers() {
        List<LayerState> result = new ArrayList<>();
        boolean hasSolo = false;

        for (LayerState layer : layers) {
            if (layer.soloButton.isSelected()) {
                hasSolo = true;
                break;
            }
        }

        for (LayerState layer : layers) {
            boolean audible = hasSolo
                    ? layer.soloButton.isSelected() && !layer.muteButton.isSelected()
                    : !layer.muteButton.isSelected();

            if (audible) {
                result.add(layer);
            }
        }

        return result;
    }

    private double outputScale(double[] mixedSamples) {
        if (!normalizeButton.isSelected()) {
            return 1.0;
        }

        double peak = 0.0;
        for (double sample : mixedSamples) {
            peak = Math.max(peak, Math.abs(sample));
        }

        return peak > 1.0 ? 0.98 / peak : 1.0;
    }

    private double envelope(LayerState layer, int sample, int sampleCount) {
        int attackSamples = SAMPLE_RATE * layer.attackSlider.getValue() / 1000;
        int releaseSamples = SAMPLE_RATE * layer.releaseSlider.getValue() / 1000;

        double attack = attackSamples <= 0
                ? 1.0
                : Math.min(1.0, sample / (double) attackSamples);

        int samplesFromEnd = sampleCount - sample - 1;
        double release = releaseSamples <= 0
                ? 1.0
                : Math.min(1.0, samplesFromEnd / (double) releaseSamples);

        return Math.max(0.0, Math.min(attack, release));
    }

    private AudioFormat createAudioFormat() {
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE,
                16,
                1,
                2,
                SAMPLE_RATE,
                false
        );
    }

    private double noteToFrequency(int midiNote) {
        return 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0);
    }

    private String sanitizeFileName(String rawName) {
        if (rawName == null) {
            return "";
        }

        return rawName
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SoundDesignerTool().setVisible(true));
    }

    private enum Waveform {
        SINE {
            @Override
            double sample(double phase) {
                return Math.sin(phase * Math.PI * 2.0);
            }
        },
        SQUARE {
            @Override
            double sample(double phase) {
                return phase < 0.5 ? 1.0 : -1.0;
            }
        },
        SAW {
            @Override
            double sample(double phase) {
                return phase * 2.0 - 1.0;
            }
        },
        TRIANGLE {
            @Override
            double sample(double phase) {
                return 1.0 - 4.0 * Math.abs(Math.round(phase - 0.25) - (phase - 0.25));
            }
        },
        NOISE {
            @Override
            double sample(double phase) {
                return Math.random() * 2.0 - 1.0;
            }
        };

        abstract double sample(double phase);
    }

    private static class LayerState {
        private final String name;
        private final int[] notes = new int[MAX_STEP_COUNT];
        private final JComboBox<Waveform> waveformBox = new JComboBox<>(Waveform.values());
        private final JSlider volumeSlider = new JSlider(0, 100, 65);
        private final JSlider attackSlider = new JSlider(0, 250, 8);
        private final JSlider releaseSlider = new JSlider(0, 600, 80);
        private final JToggleButton muteButton = new JToggleButton("Mute");
        private final JToggleButton soloButton = new JToggleButton("Solo");

        private LayerState(String name, boolean mutedByDefault) {
            this.name = name;
            muteButton.setSelected(mutedByDefault);
            volumeSlider.setPreferredSize(new Dimension(96, 24));
            attackSlider.setPreferredSize(new Dimension(96, 24));
            releaseSlider.setPreferredSize(new Dimension(96, 24));
        }
    }

    private class PitchGrid extends JComponent {
        private static final Color BACKGROUND = Color.BLACK;
        private static final Color BAR = new Color(45, 72, 150);
        private static final Color CAP = new Color(255, 20, 80);

        private int selectedStep = 0;

        private PitchGrid() {
            setPreferredSize(new Dimension(840, 420));
            setBackground(BACKGROUND);

            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    setNoteFromPoint(e.getPoint());
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    setNoteFromPoint(e.getPoint());
                }
            };

            addMouseListener(adapter);
            addMouseMotionListener(adapter);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            int width = getWidth();
            int height = getHeight();
            int left = 34;
            int right = width - 24;
            int top = 44;
            int bottom = height - 78;
            int usableWidth = right - left;
            int usableHeight = bottom - top;
            LayerState layer = currentLayer();

            g2.setColor(BACKGROUND);
            g2.fillRect(0, 0, width, height);

            drawHeader(g2, left, top, bottom, layer);

            int stepWidth = Math.max(1, usableWidth / activeStepCount);

            for (int i = 0; i < activeStepCount; i++) {
                int x = left + i * stepWidth + stepWidth / 2;
                int note = Math.max(MIN_NOTE, Math.min(MAX_NOTE, layer.notes[i]));
                double normalized = (note - MIN_NOTE) / (double) (MAX_NOTE - MIN_NOTE);
                int y = bottom - (int) Math.round(normalized * usableHeight);

                g2.setColor(i == selectedStep ? new Color(105, 120, 255) : BAR);
                g2.fillRect(x - 3, y, 6, bottom - y);

                g2.setColor(CAP);
                g2.fillRect(x - 4, y - 4, 8, 6);
            }

            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
            g2.setColor(new Color(40, 190, 255));
            g2.drawString(":VALUE", left - 10, bottom + 26);

            g2.setColor(CAP);
            g2.drawString(layer.name.toUpperCase(Locale.ROOT) + " NOTE: " + layer.notes[selectedStep], 8, height - 16);
        }

        private void drawHeader(Graphics2D g2, int left, int top, int bottom, LayerState layer) {
            g2.setColor(new Color(255, 20, 80));
            g2.fillRect(0, 0, getWidth(), 26);

            g2.setColor(new Color(98, 90, 80));
            g2.fillRect(0, 26, getWidth(), 58);

            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
            g2.setColor(new Color(170, 170, 180));
            g2.drawString(":PITCH " + layer.name.toUpperCase(Locale.ROOT), 8, 78);

            g2.setColor(new Color(255, 140, 180));
            int tickGap = Math.max(6, Math.min(12, (getWidth() - left - 24) / activeStepCount));
            for (int i = 0; i < activeStepCount; i++) {
                g2.fillRect(left + i * tickGap, bottom + 42, 8, 6);
            }

            g2.setColor(new Color(240, 240, 240));
            g2.drawString("STEPS", 115, 54);
            g2.setColor(Color.BLACK);
            g2.fillRect(188, 35, 48, 24);
            g2.setColor(Color.WHITE);
            g2.drawString(String.valueOf(activeStepCount), 194, 54);
        }

        private void setNoteFromPoint(Point point) {
            int left = 34;
            int right = getWidth() - 24;
            int top = 44;
            int bottom = getHeight() - 78;
            int usableWidth = right - left;
            int usableHeight = bottom - top;

            if (point.x < left || point.x > right) {
                return;
            }

            int step = Math.min(activeStepCount - 1, Math.max(0, (point.x - left) * activeStepCount / usableWidth));
            double normalized = 1.0 - ((point.y - top) / (double) usableHeight);
            int note = MIN_NOTE + (int) Math.round(Math.max(0.0, Math.min(1.0, normalized)) * (MAX_NOTE - MIN_NOTE));

            currentLayer().notes[step] = note;
            selectedStep = step;
            repaint();
        }

        private void clampSelectedStep() {
            selectedStep = Math.max(0, Math.min(activeStepCount - 1, selectedStep));
        }
    }
}
