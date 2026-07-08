package org.main.tools;

import org.main.engine.AssetLoader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFileFormat;
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
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class SoundDesignerTool extends JFrame {
    private static final int SAMPLE_RATE = 44_100;
    private static final int STEP_COUNT = 32;
    private static final int MIN_NOTE = 24;
    private static final int MAX_NOTE = 84;
    private static final Path OUTPUT_FOLDER = AssetLoader.generatedSoundsFolder();

    private final int[] notes = new int[STEP_COUNT];
    private final PitchGrid pitchGrid = new PitchGrid(notes);

    private final JComboBox<Waveform> waveformBox = new JComboBox<>(Waveform.values());
    private final JSlider durationSlider = new JSlider(120, 2500, 650);
    private final JSlider volumeSlider = new JSlider(0, 100, 65);
    private final JSlider attackSlider = new JSlider(0, 250, 8);
    private final JSlider releaseSlider = new JSlider(0, 600, 80);
    private final JTextField fileNameField = new JTextField("new_sound", 16);
    private final JLabel statusLabel = new JLabel("Ready.");
    private final JToggleButton loopButton = new JToggleButton("Loop");

    private SourceDataLine previewLine;
    private Thread previewThread;
    private volatile boolean previewPlaying = false;

    public SoundDesignerTool() {
        super("Sound Designer");

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(860, 560));

        initializeNotes();

        add(createToolbar(), BorderLayout.NORTH);
        add(pitchGrid, BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        toolbar.setBackground(new Color(95, 88, 79));

        JButton playButton = new JButton("Play");
        playButton.addActionListener(event -> playPreview());

        JButton stopButton = new JButton("Stop");
        stopButton.addActionListener(event -> stopPreview());

        JButton saveButton = new JButton("Save WAV");
        saveButton.addActionListener(event -> saveWav());

        JButton flattenButton = new JButton("Flatten");
        flattenButton.addActionListener(event -> {
            fillNotes(48);
            pitchGrid.repaint();
        });

        JButton rampButton = new JButton("Ramp");
        rampButton.addActionListener(event -> {
            for (int i = 0; i < notes.length; i++) {
                notes[i] = 36 + i;
            }
            pitchGrid.repaint();
        });

        toolbar.add(new JLabel("Wave"));
        toolbar.add(waveformBox);
        toolbar.add(new JLabel("Length"));
        toolbar.add(durationSlider);
        toolbar.add(new JLabel("Vol"));
        toolbar.add(volumeSlider);
        toolbar.add(new JLabel("Atk"));
        toolbar.add(attackSlider);
        toolbar.add(new JLabel("Rel"));
        toolbar.add(releaseSlider);
        toolbar.add(playButton);
        toolbar.add(stopButton);
        toolbar.add(loopButton);
        toolbar.add(flattenButton);
        toolbar.add(rampButton);
        toolbar.add(new JLabel("Name"));
        toolbar.add(fileNameField);
        toolbar.add(saveButton);

        return toolbar;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(new Color(255, 20, 80));
        footer.add(statusLabel, BorderLayout.WEST);
        return footer;
    }

    private void initializeNotes() {
        for (int i = 0; i < notes.length; i++) {
            double arc = Math.sin((i / (double) (notes.length - 1)) * Math.PI);
            notes[i] = 36 + (int) Math.round(18 * arc);
        }
    }

    private void fillNotes(int note) {
        for (int i = 0; i < notes.length; i++) {
            notes[i] = note;
        }
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
            e.printStackTrace();
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
            e.printStackTrace();
        }
    }

    private void status(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    private byte[] renderAudioBytes() {
        int durationMs = durationSlider.getValue();
        double volume = volumeSlider.getValue() / 100.0;
        int sampleCount = Math.max(1, SAMPLE_RATE * durationMs / 1000);
        byte[] audioBytes = new byte[sampleCount * 2];

        Waveform waveform = (Waveform) waveformBox.getSelectedItem();
        if (waveform == null) {
            waveform = Waveform.SINE;
        }

        double phase = 0.0;

        for (int sample = 0; sample < sampleCount; sample++) {
            double progress = sample / (double) sampleCount;
            int step = Math.min(STEP_COUNT - 1, (int) Math.floor(progress * STEP_COUNT));
            double frequency = noteToFrequency(notes[step]);
            phase += frequency / SAMPLE_RATE;
            phase -= Math.floor(phase);

            double envelope = envelope(sample, sampleCount);
            double value = waveform.sample(phase) * envelope * volume;
            short pcm = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value * Short.MAX_VALUE));

            int byteIndex = sample * 2;
            audioBytes[byteIndex] = (byte) (pcm & 0xff);
            audioBytes[byteIndex + 1] = (byte) ((pcm >> 8) & 0xff);
        }

        return audioBytes;
    }

    private double envelope(int sample, int sampleCount) {
        int attackSamples = SAMPLE_RATE * attackSlider.getValue() / 1000;
        int releaseSamples = SAMPLE_RATE * releaseSlider.getValue() / 1000;

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

    private static class PitchGrid extends JComponent {
        private static final Color BACKGROUND = Color.BLACK;
        private static final Color BAR = new Color(45, 72, 150);
        private static final Color CAP = new Color(255, 20, 80);

        private final int[] notes;
        private int selectedStep = 0;

        private PitchGrid(int[] notes) {
            this.notes = notes;
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

            g2.setColor(BACKGROUND);
            g2.fillRect(0, 0, width, height);

            drawHeader(g2, left, top, bottom);

            int stepWidth = Math.max(1, usableWidth / notes.length);

            for (int i = 0; i < notes.length; i++) {
                int x = left + i * stepWidth + stepWidth / 2;
                int note = Math.max(MIN_NOTE, Math.min(MAX_NOTE, notes[i]));
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
            g2.drawString("NOTE: " + notes[selectedStep], 8, height - 16);
        }

        private void drawHeader(Graphics2D g2, int left, int top, int bottom) {
            g2.setColor(new Color(255, 20, 80));
            g2.fillRect(0, 0, getWidth(), 26);

            g2.setColor(new Color(98, 90, 80));
            g2.fillRect(0, 26, getWidth(), 58);

            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
            g2.setColor(new Color(170, 170, 180));
            g2.drawString(":PITCH", 8, 78);

            g2.setColor(new Color(255, 140, 180));
            for (int i = 0; i < notes.length; i++) {
                g2.fillRect(left + i * 12, bottom + 42, 8, 6);
            }

            g2.setColor(new Color(240, 240, 240));
            g2.drawString("SPD", 115, 54);
            g2.setColor(Color.BLACK);
            g2.fillRect(162, 35, 38, 24);
            g2.setColor(Color.WHITE);
            g2.drawString("01", 165, 54);
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

            int step = Math.min(notes.length - 1, Math.max(0, (point.x - left) * notes.length / usableWidth));
            double normalized = 1.0 - ((point.y - top) / (double) usableHeight);
            int note = MIN_NOTE + (int) Math.round(Math.max(0.0, Math.min(1.0, normalized)) * (MAX_NOTE - MIN_NOTE));

            notes[step] = note;
            selectedStep = step;
            repaint();
        }
    }
}
