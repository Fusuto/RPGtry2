package org.main.tools;

import org.main.engine.AssetLoader;
import org.main.engine.ApplicationPaths;

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
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public class SongDesignerTool extends JFrame {
    private static final int SAMPLE_RATE = 44_100;
    private static final int TRACK_COUNT = 4;
    private static final int MIN_NOTE = 36;
    private static final int MAX_NOTE = 84;
    private static final int DEFAULT_BPM = 120;
    private static final int DEFAULT_BARS = 4;
    private static final int DEFAULT_BEATS_PER_BAR = 4;
    private static final double DEFAULT_GRID_SNAP = 0.25;
    private static final Path PROJECT_FOLDER = ApplicationPaths.dataFolder().resolve("songs").resolve("projects");
    private static final Path MUSIC_OUTPUT_FOLDER = AssetLoader.generatedSoundsFolder().resolve("music");

    private final SongProject project = SongProject.createDefault();
    private final PianoRoll pianoRoll = new PianoRoll();

    private final JTextField songNameField = new JTextField(project.name, 16);
    private final JSpinner bpmSpinner = new JSpinner(new SpinnerNumberModel(project.bpm, 40, 240, 1));
    private final JSpinner barsSpinner = new JSpinner(new SpinnerNumberModel(project.barCount, 1, 64, 1));
    private final JSpinner beatsPerBarSpinner = new JSpinner(new SpinnerNumberModel(project.beatsPerBar, 1, 8, 1));
    private final JComboBox<GridSnap> gridSnapBox = new JComboBox<>(GridSnap.values());
    private final JComboBox<String> trackBox = new JComboBox<>(new String[]{
            "Melody",
            "Harmony",
            "Bass",
            "Noise/Drums"
    });
    private final JComboBox<Waveform> waveformBox = new JComboBox<>(Waveform.values());
    private final JSlider volumeSlider = new JSlider(0, 100, 70);
    private final JSlider attackSlider = new JSlider(0, 250, 8);
    private final JSlider releaseSlider = new JSlider(0, 700, 80);
    private final JToggleButton muteButton = new JToggleButton("Mute");
    private final JToggleButton soloButton = new JToggleButton("Solo");
    private final JToggleButton loopButton = new JToggleButton("Loop");
    private final JToggleButton normalizeButton = new JToggleButton("Normalize", true);
    private final JLabel statusLabel = new JLabel("Ready.");

    private SourceDataLine previewLine;
    private Thread previewThread;
    private volatile boolean previewPlaying = false;
    private int selectedTrackIndex = 0;

    public SongDesignerTool() {
        super("Song Designer");

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(1100, 680));

        gridSnapBox.setSelectedItem(GridSnap.SIXTEENTH);
        configureSliders();
        add(createControlPanel(), BorderLayout.NORTH);
        add(pianoRoll, BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);
        syncControlsFromTrack();

        pack();
        setLocationRelativeTo(null);
    }

    private void configureSliders() {
        volumeSlider.setPreferredSize(new Dimension(110, 24));
        attackSlider.setPreferredSize(new Dimension(110, 24));
        releaseSlider.setPreferredSize(new Dimension(110, 24));
    }

    private JPanel createControlPanel() {
        JPanel host = new JPanel(new BorderLayout());
        host.add(createSongToolbar(), BorderLayout.NORTH);
        host.add(createTrackToolbar(), BorderLayout.SOUTH);
        return host;
    }

    private JPanel createSongToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbar.setBackground(new Color(92, 86, 78));

        JButton playButton = new JButton("Play");
        playButton.addActionListener(event -> playPreview());

        JButton stopButton = new JButton("Stop");
        stopButton.addActionListener(event -> stopPreview());

        JButton saveButton = new JButton("Save Project");
        saveButton.addActionListener(event -> saveProject());

        JButton loadButton = new JButton("Load Project");
        loadButton.addActionListener(event -> loadProject());

        JButton exportButton = new JButton("Export WAV");
        exportButton.addActionListener(event -> exportWav());

        bpmSpinner.addChangeListener(event -> {
            project.bpm = ((Number) bpmSpinner.getValue()).intValue();
            pianoRoll.repaint();
        });
        barsSpinner.addChangeListener(event -> {
            project.barCount = ((Number) barsSpinner.getValue()).intValue();
            pianoRoll.repaint();
        });
        beatsPerBarSpinner.addChangeListener(event -> {
            project.beatsPerBar = ((Number) beatsPerBarSpinner.getValue()).intValue();
            pianoRoll.repaint();
        });
        gridSnapBox.addActionListener(event -> {
            GridSnap snap = (GridSnap) gridSnapBox.getSelectedItem();
            project.gridResolution = snap == null ? DEFAULT_GRID_SNAP : snap.beats;
            pianoRoll.repaint();
        });

        toolbar.add(new JLabel("Song"));
        toolbar.add(songNameField);
        toolbar.add(new JLabel("BPM"));
        toolbar.add(bpmSpinner);
        toolbar.add(new JLabel("Bars"));
        toolbar.add(barsSpinner);
        toolbar.add(new JLabel("Beats"));
        toolbar.add(beatsPerBarSpinner);
        toolbar.add(new JLabel("Snap"));
        toolbar.add(gridSnapBox);
        toolbar.add(playButton);
        toolbar.add(stopButton);
        toolbar.add(loopButton);
        toolbar.add(normalizeButton);
        toolbar.add(saveButton);
        toolbar.add(loadButton);
        toolbar.add(exportButton);

        return toolbar;
    }

    private JPanel createTrackToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolbar.setBackground(new Color(74, 69, 63));

        trackBox.addActionListener(event -> {
            syncTrackFromControls();
            selectedTrackIndex = Math.max(0, trackBox.getSelectedIndex());
            syncControlsFromTrack();
            pianoRoll.repaint();
        });

        waveformBox.addActionListener(event -> syncTrackFromControls());
        volumeSlider.addChangeListener(event -> syncTrackFromControls());
        attackSlider.addChangeListener(event -> syncTrackFromControls());
        releaseSlider.addChangeListener(event -> syncTrackFromControls());
        muteButton.addActionListener(event -> syncTrackFromControls());
        soloButton.addActionListener(event -> syncTrackFromControls());

        JButton clearButton = new JButton("Clear Track");
        clearButton.addActionListener(event -> {
            currentTrack().notes.clear();
            pianoRoll.selectNote(null);
            pianoRoll.repaint();
        });

        toolbar.add(new JLabel("Track"));
        toolbar.add(trackBox);
        toolbar.add(new JLabel("Wave"));
        toolbar.add(waveformBox);
        toolbar.add(new JLabel("Vol"));
        toolbar.add(volumeSlider);
        toolbar.add(new JLabel("Atk"));
        toolbar.add(attackSlider);
        toolbar.add(new JLabel("Rel"));
        toolbar.add(releaseSlider);
        toolbar.add(muteButton);
        toolbar.add(soloButton);
        toolbar.add(clearButton);

        return toolbar;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(new Color(255, 20, 80));
        footer.add(statusLabel, BorderLayout.WEST);
        return footer;
    }

    private SongTrack currentTrack() {
        return project.tracks.get(Math.max(0, Math.min(project.tracks.size() - 1, selectedTrackIndex)));
    }

    private void syncControlsFromTrack() {
        SongTrack track = currentTrack();
        waveformBox.setSelectedItem(track.waveform);
        volumeSlider.setValue(track.volume);
        attackSlider.setValue(track.attackMs);
        releaseSlider.setValue(track.releaseMs);
        muteButton.setSelected(track.muted);
        soloButton.setSelected(track.solo);
    }

    private void syncTrackFromControls() {
        SongTrack track = currentTrack();
        Object waveform = waveformBox.getSelectedItem();
        track.waveform = waveform instanceof Waveform selected ? selected : Waveform.SQUARE;
        track.volume = volumeSlider.getValue();
        track.attackMs = attackSlider.getValue();
        track.releaseMs = releaseSlider.getValue();
        track.muted = muteButton.isSelected();
        track.solo = soloButton.isSelected();
    }

    private void playPreview() {
        stopPreview();
        syncProjectFromControls();

        byte[] audioBytes = SongRenderer.render(project, normalizeButton.isSelected());
        AudioFormat format = createAudioFormat();

        previewPlaying = true;
        previewThread = new Thread(() -> streamPreview(format, audioBytes), "song-preview");
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

            status("Playing song preview.");

            int chunkSize = 4096;
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

    private void saveProject() {
        syncProjectFromControls();
        Path path = projectPath();

        try {
            Files.createDirectories(PROJECT_FOLDER);
            Properties properties = SongProjectCodec.toProperties(project);

            try (OutputStream outputStream = Files.newOutputStream(path)) {
                properties.store(outputStream, "Aether song project");
            }

            status("Saved project " + path);
        } catch (Exception e) {
            status("Save failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadProject() {
        Path path = projectPath();

        if (!Files.exists(path)) {
            status("Project not found: " + path);
            return;
        }

        try {
            Properties properties = new Properties();
            try (InputStream inputStream = Files.newInputStream(path)) {
                properties.load(inputStream);
            }

            SongProject loaded = SongProjectCodec.fromProperties(properties);
            project.copyFrom(loaded);
            syncControlsFromProject();
            pianoRoll.selectNote(null);
            pianoRoll.repaint();
            status("Loaded project " + path);
        } catch (Exception e) {
            status("Load failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void exportWav() {
        syncProjectFromControls();
        byte[] audioBytes = SongRenderer.render(project, normalizeButton.isSelected());
        AudioFormat format = createAudioFormat();
        Path outputPath = MUSIC_OUTPUT_FOLDER.resolve(sanitizeFileName(project.name) + ".wav");

        try {
            Files.createDirectories(MUSIC_OUTPUT_FOLDER);

            AudioInputStream audioInputStream = new AudioInputStream(
                    new ByteArrayInputStream(audioBytes),
                    format,
                    audioBytes.length / format.getFrameSize()
            );

            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputPath.toFile());
            status("Exported WAV " + outputPath);
        } catch (Exception e) {
            status("Export failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void syncProjectFromControls() {
        project.name = songNameField.getText();
        project.bpm = ((Number) bpmSpinner.getValue()).intValue();
        project.barCount = ((Number) barsSpinner.getValue()).intValue();
        project.beatsPerBar = ((Number) beatsPerBarSpinner.getValue()).intValue();
        GridSnap snap = (GridSnap) gridSnapBox.getSelectedItem();
        project.gridResolution = snap == null ? DEFAULT_GRID_SNAP : snap.beats;
        syncTrackFromControls();

        if (project.name == null || project.name.isBlank()) {
            project.name = "new_song";
            songNameField.setText(project.name);
        }
    }

    private void syncControlsFromProject() {
        songNameField.setText(project.name);
        bpmSpinner.setValue(project.bpm);
        barsSpinner.setValue(project.barCount);
        beatsPerBarSpinner.setValue(project.beatsPerBar);
        gridSnapBox.setSelectedItem(GridSnap.closest(project.gridResolution));
        selectedTrackIndex = 0;
        trackBox.setSelectedIndex(0);
        syncControlsFromTrack();
    }

    private Path projectPath() {
        String fileName = sanitizeFileName(songNameField.getText());
        if (fileName.isBlank()) {
            fileName = "new_song";
        }

        return PROJECT_FOLDER.resolve(fileName + ".song.properties");
    }

    private void status(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    private static AudioFormat createAudioFormat() {
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

    private static String sanitizeFileName(String rawName) {
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
        SwingUtilities.invokeLater(() -> new SongDesignerTool().setVisible(true));
    }

    public static class SongProject {
        private String name;
        private int bpm;
        private int barCount;
        private int beatsPerBar;
        private double gridResolution;
        private final List<SongTrack> tracks = new ArrayList<>();

        private static SongProject createDefault() {
            SongProject project = new SongProject();
            project.name = "new_song";
            project.bpm = DEFAULT_BPM;
            project.barCount = DEFAULT_BARS;
            project.beatsPerBar = DEFAULT_BEATS_PER_BAR;
            project.gridResolution = DEFAULT_GRID_SNAP;
            project.tracks.add(new SongTrack("Melody", Waveform.SQUARE, 72, 8, 80));
            project.tracks.add(new SongTrack("Harmony", Waveform.TRIANGLE, 55, 10, 120));
            project.tracks.add(new SongTrack("Bass", Waveform.SAW, 68, 4, 90));
            project.tracks.add(new SongTrack("Noise/Drums", Waveform.NOISE, 45, 0, 40));
            return project;
        }

        private double totalBeats() {
            return Math.max(1, barCount * beatsPerBar);
        }

        private void copyFrom(SongProject other) {
            name = other.name;
            bpm = other.bpm;
            barCount = other.barCount;
            beatsPerBar = other.beatsPerBar;
            gridResolution = other.gridResolution;
            tracks.clear();
            for (SongTrack track : other.tracks) {
                tracks.add(track.copy());
            }

            while (tracks.size() < TRACK_COUNT) {
                tracks.add(new SongTrack("Track " + (tracks.size() + 1), Waveform.SQUARE, 65, 8, 80));
            }
        }
    }

    public static class SongTrack {
        private String name;
        private Waveform waveform;
        private int volume;
        private int attackMs;
        private int releaseMs;
        private boolean muted;
        private boolean solo;
        private final List<NoteEvent> notes = new ArrayList<>();

        private SongTrack(String name, Waveform waveform, int volume, int attackMs, int releaseMs) {
            this.name = name;
            this.waveform = waveform;
            this.volume = volume;
            this.attackMs = attackMs;
            this.releaseMs = releaseMs;
        }

        private SongTrack copy() {
            SongTrack copy = new SongTrack(name, waveform, volume, attackMs, releaseMs);
            copy.muted = muted;
            copy.solo = solo;
            for (NoteEvent note : notes) {
                copy.notes.add(note.copy());
            }
            return copy;
        }
    }

    public static class NoteEvent {
        private double startBeat;
        private double durationBeats;
        private int midiNote;
        private int velocity;

        private NoteEvent(double startBeat, double durationBeats, int midiNote, int velocity) {
            this.startBeat = startBeat;
            this.durationBeats = durationBeats;
            this.midiNote = midiNote;
            this.velocity = velocity;
        }

        private NoteEvent copy() {
            return new NoteEvent(startBeat, durationBeats, midiNote, velocity);
        }
    }

    private enum GridSnap {
        WHOLE("1 beat", 1.0),
        HALF("1/2", 0.5),
        SIXTEENTH("1/4", 0.25),
        THIRTY_SECOND("1/8", 0.125);

        private final String label;
        private final double beats;

        GridSnap(String label, double beats) {
            this.label = label;
            this.beats = beats;
        }

        private static GridSnap closest(double beats) {
            GridSnap closest = SIXTEENTH;
            double bestDistance = Double.MAX_VALUE;

            for (GridSnap snap : values()) {
                double distance = Math.abs(snap.beats - beats);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    closest = snap;
                }
            }

            return closest;
        }

        @Override
        public String toString() {
            return label;
        }
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

    private static class SongRenderer {
        private static byte[] render(SongProject project, boolean normalize) {
            int sampleCount = Math.max(1, (int) Math.ceil(project.totalBeats() * 60.0 / project.bpm * SAMPLE_RATE));
            double[] mixedSamples = new double[sampleCount];
            List<SongTrack> audibleTracks = audibleTracks(project);

            for (SongTrack track : audibleTracks) {
                for (NoteEvent note : track.notes) {
                    renderNote(project, track, note, mixedSamples);
                }
            }

            double scale = outputScale(mixedSamples, normalize);
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

        private static List<SongTrack> audibleTracks(SongProject project) {
            boolean hasSolo = project.tracks.stream().anyMatch(track -> track.solo);
            return project.tracks.stream()
                    .filter(track -> hasSolo ? track.solo && !track.muted : !track.muted)
                    .toList();
        }

        private static void renderNote(SongProject project, SongTrack track, NoteEvent note, double[] mixedSamples) {
            double secondsPerBeat = 60.0 / project.bpm;
            int startSample = Math.max(0, (int) Math.round(note.startBeat * secondsPerBeat * SAMPLE_RATE));
            int noteSamples = Math.max(1, (int) Math.round(note.durationBeats * secondsPerBeat * SAMPLE_RATE));
            int endSample = Math.min(mixedSamples.length, startSample + noteSamples);
            double frequency = noteToFrequency(note.midiNote);
            double volume = (track.volume / 100.0) * (note.velocity / 100.0);
            double phase = 0.0;

            for (int sample = startSample; sample < endSample; sample++) {
                int localSample = sample - startSample;
                phase += frequency / SAMPLE_RATE;
                phase -= Math.floor(phase);
                mixedSamples[sample] += track.waveform.sample(phase) * noteEnvelope(track, localSample, noteSamples) * volume;
            }
        }

        private static double noteEnvelope(SongTrack track, int sample, int sampleCount) {
            int attackSamples = SAMPLE_RATE * Math.max(0, track.attackMs) / 1000;
            int releaseSamples = SAMPLE_RATE * Math.max(0, track.releaseMs) / 1000;

            double attack = attackSamples <= 0
                    ? 1.0
                    : Math.min(1.0, sample / (double) attackSamples);
            int samplesFromEnd = sampleCount - sample - 1;
            double release = releaseSamples <= 0
                    ? 1.0
                    : Math.min(1.0, samplesFromEnd / (double) releaseSamples);

            return Math.max(0.0, Math.min(attack, release));
        }

        private static double outputScale(double[] mixedSamples, boolean normalize) {
            if (!normalize) {
                return 1.0;
            }

            double peak = 0.0;
            for (double sample : mixedSamples) {
                peak = Math.max(peak, Math.abs(sample));
            }

            return peak > 1.0 ? 0.98 / peak : 1.0;
        }

        private static double noteToFrequency(int midiNote) {
            return 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0);
        }
    }

    private static class SongProjectCodec {
        private static Properties toProperties(SongProject project) {
            Properties properties = new Properties();
            properties.setProperty("name", project.name);
            properties.setProperty("bpm", String.valueOf(project.bpm));
            properties.setProperty("barCount", String.valueOf(project.barCount));
            properties.setProperty("beatsPerBar", String.valueOf(project.beatsPerBar));
            properties.setProperty("gridResolution", String.valueOf(project.gridResolution));
            properties.setProperty("trackCount", String.valueOf(project.tracks.size()));

            for (int i = 0; i < project.tracks.size(); i++) {
                SongTrack track = project.tracks.get(i);
                String prefix = "track." + i + ".";
                properties.setProperty(prefix + "name", track.name);
                properties.setProperty(prefix + "waveform", track.waveform.name());
                properties.setProperty(prefix + "volume", String.valueOf(track.volume));
                properties.setProperty(prefix + "attackMs", String.valueOf(track.attackMs));
                properties.setProperty(prefix + "releaseMs", String.valueOf(track.releaseMs));
                properties.setProperty(prefix + "muted", String.valueOf(track.muted));
                properties.setProperty(prefix + "solo", String.valueOf(track.solo));
                properties.setProperty(prefix + "noteCount", String.valueOf(track.notes.size()));

                for (int j = 0; j < track.notes.size(); j++) {
                    NoteEvent note = track.notes.get(j);
                    properties.setProperty(
                            prefix + "note." + j,
                            note.startBeat + "," + note.durationBeats + "," + note.midiNote + "," + note.velocity
                    );
                }
            }

            return properties;
        }

        private static SongProject fromProperties(Properties properties) {
            SongProject project = SongProject.createDefault();
            project.name = properties.getProperty("name", "new_song");
            project.bpm = readInt(properties, "bpm", DEFAULT_BPM);
            project.barCount = readInt(properties, "barCount", DEFAULT_BARS);
            project.beatsPerBar = readInt(properties, "beatsPerBar", DEFAULT_BEATS_PER_BAR);
            project.gridResolution = readDouble(properties, "gridResolution", DEFAULT_GRID_SNAP);
            project.tracks.clear();

            int trackCount = Math.max(TRACK_COUNT, readInt(properties, "trackCount", TRACK_COUNT));
            for (int i = 0; i < trackCount; i++) {
                String prefix = "track." + i + ".";
                SongTrack track = new SongTrack(
                        properties.getProperty(prefix + "name", "Track " + (i + 1)),
                        readEnum(properties, prefix + "waveform", Waveform.class, Waveform.SQUARE),
                        readInt(properties, prefix + "volume", 65),
                        readInt(properties, prefix + "attackMs", 8),
                        readInt(properties, prefix + "releaseMs", 80)
                );
                track.muted = Boolean.parseBoolean(properties.getProperty(prefix + "muted", "false"));
                track.solo = Boolean.parseBoolean(properties.getProperty(prefix + "solo", "false"));

                int noteCount = readInt(properties, prefix + "noteCount", 0);
                for (int j = 0; j < noteCount; j++) {
                    NoteEvent note = readNote(properties.getProperty(prefix + "note." + j, ""));
                    if (note != null) {
                        track.notes.add(note);
                    }
                }

                project.tracks.add(track);
            }

            return project;
        }

        private static NoteEvent readNote(String value) {
            String[] parts = value.split(",");
            if (parts.length != 4) {
                return null;
            }

            try {
                return new NoteEvent(
                        Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])
                );
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private static int readInt(Properties properties, String key, int fallback) {
            try {
                return Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private static double readDouble(Properties properties, String key, double fallback) {
            try {
                return Double.parseDouble(properties.getProperty(key, String.valueOf(fallback)));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private static <T extends Enum<T>> T readEnum(Properties properties, String key, Class<T> type, T fallback) {
            try {
                return Enum.valueOf(type, properties.getProperty(key, fallback.name()));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    private class PianoRoll extends JComponent {
        private static final int LEFT_MARGIN = 58;
        private static final int TOP_MARGIN = 24;
        private static final int RIGHT_MARGIN = 22;
        private static final int BOTTOM_MARGIN = 36;
        private static final int EDGE_HIT_WIDTH = 7;
        private static final int MIN_NOTE_WIDTH = 6;

        private NoteEvent selectedNote;
        private DragMode dragMode = DragMode.NONE;
        private double dragStartBeat;
        private int dragStartNote;
        private double originalStartBeat;
        private double originalDurationBeats;
        private int originalMidiNote;

        private PianoRoll() {
            setPreferredSize(new Dimension(1040, 520));
            setBackground(Color.BLACK);
            setFocusable(true);

            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    handleMousePressed(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    handleMouseDragged(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragMode = DragMode.NONE;
                }
            };

            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if ((e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE)
                            && selectedNote != null) {
                        currentTrack().notes.remove(selectedNote);
                        selectedNote = null;
                        repaint();
                    }
                }
            });
        }

        private void selectNote(NoteEvent note) {
            selectedNote = note;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            int width = getWidth();
            int height = getHeight();
            Rectangle grid = gridBounds();

            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, width, height);
            drawGrid(g2, grid);
            drawNotes(g2, grid);
            drawLabels(g2, grid);
        }

        private Rectangle gridBounds() {
            return new Rectangle(
                    LEFT_MARGIN,
                    TOP_MARGIN,
                    Math.max(1, getWidth() - LEFT_MARGIN - RIGHT_MARGIN),
                    Math.max(1, getHeight() - TOP_MARGIN - BOTTOM_MARGIN)
            );
        }

        private void drawGrid(Graphics2D g2, Rectangle grid) {
            g2.setColor(new Color(12, 12, 16));
            g2.fillRect(grid.x, grid.y, grid.width, grid.height);

            double totalBeats = project.totalBeats();
            int noteRange = MAX_NOTE - MIN_NOTE + 1;

            for (int note = MIN_NOTE; note <= MAX_NOTE; note++) {
                int y = yForNote(grid, note);
                boolean octave = note % 12 == 0;
                g2.setColor(octave ? new Color(42, 48, 62) : new Color(28, 31, 39));
                g2.drawLine(grid.x, y, grid.x + grid.width, y);
            }

            for (double beat = 0.0; beat <= totalBeats + 0.001; beat += project.gridResolution) {
                int x = xForBeat(grid, beat);
                boolean barLine = Math.abs(beat % project.beatsPerBar) < 0.001;
                boolean beatLine = Math.abs(beat - Math.round(beat)) < 0.001;
                g2.setColor(barLine ? new Color(95, 76, 54) : beatLine ? new Color(50, 53, 64) : new Color(28, 31, 39));
                g2.drawLine(x, grid.y, x, grid.y + grid.height);
            }

            g2.setColor(new Color(105, 88, 62));
            g2.drawRect(grid.x, grid.y, grid.width, grid.height);

            g2.setColor(new Color(28, 28, 34));
            g2.fillRect(0, grid.y, LEFT_MARGIN, grid.height);
            g2.setColor(new Color(170, 36, 32));
            g2.fillRect(0, 0, getWidth(), 18);
            g2.setColor(new Color(255, 20, 80));
            g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        }

        private void drawNotes(Graphics2D g2, Rectangle grid) {
            List<NoteEvent> notes = new ArrayList<>(currentTrack().notes);
            notes.sort(Comparator.comparingDouble(note -> note.startBeat));

            for (NoteEvent note : notes) {
                Rectangle bounds = noteBounds(grid, note);
                boolean selected = note == selectedNote;
                g2.setColor(selected ? new Color(255, 110, 160) : new Color(70, 150, 245));
                g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                g2.setColor(selected ? Color.WHITE : new Color(16, 20, 28));
                g2.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

                if (selected) {
                    g2.setColor(new Color(255, 230, 150));
                    g2.fillRect(bounds.x, bounds.y, 3, bounds.height);
                    g2.fillRect(bounds.x + bounds.width - 3, bounds.y, 3, bounds.height);
                }
            }
        }

        private void drawLabels(Graphics2D g2, Rectangle grid) {
            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
            FontMetrics metrics = g2.getFontMetrics();

            for (int note = MIN_NOTE; note <= MAX_NOTE; note += 12) {
                int y = yForNote(grid, note);
                String label = noteName(note);
                g2.setColor(new Color(190, 190, 200));
                g2.drawString(label, LEFT_MARGIN - metrics.stringWidth(label) - 8, y - 3);
            }

            g2.setColor(new Color(238, 228, 190));
            for (int bar = 0; bar < project.barCount; bar++) {
                double beat = bar * project.beatsPerBar;
                int x = xForBeat(grid, beat) + 4;
                g2.drawString(String.valueOf(bar + 1), x, grid.y + grid.height + 18);
            }

            String footer = currentTrack().name + " | click-drag notes | right-click/Delete removes";
            g2.setColor(new Color(255, 20, 80));
            g2.drawString(footer, 8, getHeight() - 10);
        }

        private void handleMousePressed(MouseEvent e) {
            Rectangle grid = gridBounds();
            NoteEvent hit = findNoteAt(grid, e.getPoint());

            if (SwingUtilities.isRightMouseButton(e)) {
                if (hit != null) {
                    currentTrack().notes.remove(hit);
                    if (selectedNote == hit) {
                        selectedNote = null;
                    }
                    repaint();
                }
                return;
            }

            if (!grid.contains(e.getPoint())) {
                return;
            }

            if (hit != null) {
                selectedNote = hit;
                Rectangle bounds = noteBounds(grid, hit);
                if (Math.abs(e.getX() - bounds.x) <= EDGE_HIT_WIDTH) {
                    dragMode = DragMode.RESIZE_LEFT;
                } else if (Math.abs(e.getX() - (bounds.x + bounds.width)) <= EDGE_HIT_WIDTH) {
                    dragMode = DragMode.RESIZE_RIGHT;
                } else {
                    dragMode = DragMode.MOVE;
                }
            } else {
                double startBeat = snapBeat(beatForX(grid, e.getX()));
                int midiNote = noteForY(grid, e.getY());
                NoteEvent note = new NoteEvent(
                        clampBeat(startBeat),
                        project.gridResolution,
                        midiNote,
                        100
                );
                currentTrack().notes.add(note);
                selectedNote = note;
                dragMode = DragMode.RESIZE_RIGHT;
            }

            dragStartBeat = beatForX(grid, e.getX());
            dragStartNote = noteForY(grid, e.getY());
            originalStartBeat = selectedNote.startBeat;
            originalDurationBeats = selectedNote.durationBeats;
            originalMidiNote = selectedNote.midiNote;
            repaint();
        }

        private void handleMouseDragged(MouseEvent e) {
            if (selectedNote == null || dragMode == DragMode.NONE) {
                return;
            }

            Rectangle grid = gridBounds();
            double beat = beatForX(grid, e.getX());
            double beatDelta = snapBeat(beat - dragStartBeat);
            int noteDelta = noteForY(grid, e.getY()) - dragStartNote;

            if (dragMode == DragMode.MOVE) {
                selectedNote.startBeat = clampBeat(snapBeat(originalStartBeat + beatDelta));
                selectedNote.midiNote = Math.max(MIN_NOTE, Math.min(MAX_NOTE, originalMidiNote + noteDelta));
            } else if (dragMode == DragMode.RESIZE_RIGHT) {
                double endBeat = snapBeat(originalStartBeat + originalDurationBeats + beatDelta);
                selectedNote.durationBeats = Math.max(project.gridResolution, endBeat - selectedNote.startBeat);
            } else if (dragMode == DragMode.RESIZE_LEFT) {
                double originalEndBeat = originalStartBeat + originalDurationBeats;
                double newStart = clampBeat(snapBeat(originalStartBeat + beatDelta));
                if (newStart < originalEndBeat - project.gridResolution) {
                    selectedNote.startBeat = newStart;
                    selectedNote.durationBeats = originalEndBeat - newStart;
                }
            }

            selectedNote.durationBeats = Math.min(selectedNote.durationBeats, project.totalBeats() - selectedNote.startBeat);
            repaint();
        }

        private NoteEvent findNoteAt(Rectangle grid, Point point) {
            List<NoteEvent> notes = currentTrack().notes;
            for (int i = notes.size() - 1; i >= 0; i--) {
                NoteEvent note = notes.get(i);
                if (noteBounds(grid, note).contains(point)) {
                    return note;
                }
            }

            return null;
        }

        private Rectangle noteBounds(Rectangle grid, NoteEvent note) {
            int x = xForBeat(grid, note.startBeat);
            int endX = xForBeat(grid, note.startBeat + note.durationBeats);
            int y = yForNote(grid, note.midiNote);
            int rowHeight = Math.max(8, grid.height / (MAX_NOTE - MIN_NOTE + 1));
            return new Rectangle(x, y - rowHeight + 1, Math.max(MIN_NOTE_WIDTH, endX - x), Math.max(6, rowHeight - 2));
        }

        private int xForBeat(Rectangle grid, double beat) {
            return grid.x + (int) Math.round((beat / project.totalBeats()) * grid.width);
        }

        private double beatForX(Rectangle grid, int x) {
            double normalized = (x - grid.x) / (double) grid.width;
            return Math.max(0.0, Math.min(project.totalBeats(), normalized * project.totalBeats()));
        }

        private int yForNote(Rectangle grid, int note) {
            double normalized = (note - MIN_NOTE) / (double) (MAX_NOTE - MIN_NOTE + 1);
            return grid.y + grid.height - (int) Math.round(normalized * grid.height);
        }

        private int noteForY(Rectangle grid, int y) {
            double normalized = 1.0 - ((y - grid.y) / (double) grid.height);
            int note = MIN_NOTE + (int) Math.floor(normalized * (MAX_NOTE - MIN_NOTE + 1));
            return Math.max(MIN_NOTE, Math.min(MAX_NOTE, note));
        }

        private double snapBeat(double beat) {
            double snap = Math.max(0.03125, project.gridResolution);
            return Math.round(beat / snap) * snap;
        }

        private double clampBeat(double beat) {
            return Math.max(0.0, Math.min(project.totalBeats() - project.gridResolution, beat));
        }

        private String noteName(int midiNote) {
            String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
            return names[Math.floorMod(midiNote, 12)] + (midiNote / 12 - 1);
        }
    }

    private enum DragMode {
        NONE,
        MOVE,
        RESIZE_LEFT,
        RESIZE_RIGHT
    }
}
