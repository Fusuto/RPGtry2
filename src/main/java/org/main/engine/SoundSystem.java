package org.main.engine;

import org.main.core.GameConfiguration;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SoundSystem {
    private static final Logger LOGGER = Logger.getLogger(SoundSystem.class.getName());
    private final ExecutorService audioLoader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Aether-Audio-Loader");
        thread.setDaemon(true);
        return thread;
    });

    private Clip ambienceClip;
    private String ambiencePath;
    private String ambienceLoadingPath;
    private int ambienceRequest;

    private Clip musicClip;
    private String musicPath;
    private String musicLoadingPath;
    private int musicRequest;

    private Clip loopingSoundClip;
    private String loopingSoundPath;

    private double ambienceVolume = defaultVolume();
    private double musicVolume = defaultVolume();
    private double soundEffectVolume = defaultVolume();

    private static double defaultVolume() {
        return Math.max(0.0, Math.min(1.0, GameConfiguration.doubleValue("sound.defaultVolume", 0.20)));
    }

    public void playSound(String soundPath) {
        if (isBlank(soundPath)) {
            return;
        }
        audioLoader.execute(() -> {
            Clip clip = loadClip(soundPath);
            if (clip == null) {
                return;
            }
            applyVolume(clip, soundEffectVolume);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.start();
        });
    }

    public void playLoopingSound(String soundPath) {
        if (samePath(loopingSoundPath, soundPath) && isClipRunning(loopingSoundClip)) {
            return;
        }

        stopLoopingSound();

        loopingSoundPath = soundPath;
        loopingSoundClip = loadLoopingClip(soundPath);
        applyVolume(loopingSoundClip, soundEffectVolume);
    }

    public void stopLoopingSound() {
        stopClip(loopingSoundClip);
        loopingSoundClip = null;
        loopingSoundPath = null;
    }

    public synchronized void playAmbience(String soundPath) {
        if (samePath(ambiencePath, soundPath)
                && (isClipRunning(ambienceClip) || samePath(ambienceLoadingPath, soundPath))) {
            return;
        }
        int request = ++ambienceRequest;
        Clip previous = ambienceClip;
        ambienceClip = null;
        ambiencePath = soundPath;
        ambienceLoadingPath = isBlank(soundPath) ? null : soundPath;
        audioLoader.execute(() -> replaceAmbience(previous, soundPath, request));
    }

    public synchronized void stopAmbience() {
        ambienceRequest++;
        Clip previous = ambienceClip;
        ambienceClip = null;
        ambiencePath = null;
        ambienceLoadingPath = null;
        audioLoader.execute(() -> stopClip(previous));
    }

    public synchronized void playMusic(String soundPath) {
        if (samePath(musicPath, soundPath)
                && (isClipRunning(musicClip) || samePath(musicLoadingPath, soundPath))) {
            return;
        }
        int request = ++musicRequest;
        Clip previous = musicClip;
        musicClip = null;
        musicPath = soundPath;
        musicLoadingPath = isBlank(soundPath) ? null : soundPath;
        audioLoader.execute(() -> replaceMusic(previous, soundPath, request));
    }

    public synchronized void stopMusic() {
        musicRequest++;
        Clip previous = musicClip;
        musicClip = null;
        musicPath = null;
        musicLoadingPath = null;
        audioLoader.execute(() -> stopClip(previous));
    }

    public void stopAll() {
        stopAmbience();
        stopMusic();
        stopLoopingSound();
    }

    public double getAmbienceVolume() {
        return ambienceVolume;
    }

    public String getAmbiencePath() {
        return ambiencePath;
    }

    public boolean isAmbienceRunning() {
        return isClipRunning(ambienceClip);
    }

    public void setAmbienceVolume(double ambienceVolume) {
        this.ambienceVolume = clampVolume(ambienceVolume);
        applyVolume(ambienceClip, this.ambienceVolume);
    }

    public void adjustAmbienceVolume(double amount) {
        setAmbienceVolume(ambienceVolume + amount);
    }

    public double getMusicVolume() {
        return musicVolume;
    }

    public String getMusicPath() {
        return musicPath;
    }

    public boolean isMusicRunning() {
        return isClipRunning(musicClip);
    }

    public void setMusicVolume(double musicVolume) {
        this.musicVolume = clampVolume(musicVolume);
        applyVolume(musicClip, this.musicVolume);
    }

    public void adjustMusicVolume(double amount) {
        setMusicVolume(musicVolume + amount);
    }

    public double getSoundEffectVolume() {
        return soundEffectVolume;
    }

    public String getLoopingSoundPath() {
        return loopingSoundPath;
    }

    public boolean isLoopingSoundRunning() {
        return isClipRunning(loopingSoundClip);
    }

    public void setSoundEffectVolume(double soundEffectVolume) {
        this.soundEffectVolume = clampVolume(soundEffectVolume);
        applyVolume(loopingSoundClip, this.soundEffectVolume);
    }

    public void adjustSoundEffectVolume(double amount) {
        setSoundEffectVolume(soundEffectVolume + amount);
    }

    private Clip loadLoopingClip(String soundPath) {
        if (isBlank(soundPath)) {
            return null;
        }

        Clip clip = loadClip(soundPath);

        if (clip == null) {
            return null;
        }

        clip.loop(Clip.LOOP_CONTINUOUSLY);
        return clip;
    }

    private void replaceAmbience(Clip previous, String requestedPath, int request) {
        stopClip(previous);
        Clip loaded = isBlank(requestedPath) ? null : loadClip(requestedPath);
        synchronized (this) {
            if (request != ambienceRequest || !samePath(ambiencePath, requestedPath)) {
                stopClip(loaded);
                return;
            }
            ambienceLoadingPath = null;
            ambienceClip = loaded;
            applyVolume(ambienceClip, ambienceVolume);
            if (ambienceClip != null) {
                ambienceClip.loop(Clip.LOOP_CONTINUOUSLY);
            }
        }
    }

    private void replaceMusic(Clip previous, String requestedPath, int request) {
        stopClip(previous);
        Clip loaded = isBlank(requestedPath) ? null : loadClip(requestedPath);
        synchronized (this) {
            if (request != musicRequest || !samePath(musicPath, requestedPath)) {
                stopClip(loaded);
                return;
            }
            musicLoadingPath = null;
            musicClip = loaded;
            applyVolume(musicClip, musicVolume);
            if (musicClip != null) {
                musicClip.loop(Clip.LOOP_CONTINUOUSLY);
            }
        }
    }

    private Clip loadClip(String soundPath) {
        try {
            AudioInputStream audioInputStream = createPlayableAudioStream(soundPath);
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            return clip;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load sound: " + soundPath, e);
            return null;
        }
    }

    private AudioInputStream createPlayableAudioStream(String soundPath) throws Exception {
        AudioInputStream sourceStream = AssetLoader.openAudioStream(soundPath);
        AudioFormat sourceFormat = sourceStream.getFormat();

        if (isClipFriendlyFormat(sourceFormat)) {
            return sourceStream;
        }

        AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sourceFormat.getSampleRate(),
                16,
                sourceFormat.getChannels(),
                sourceFormat.getChannels() * 2,
                sourceFormat.getSampleRate(),
                false
        );

        if (!AudioSystem.isConversionSupported(targetFormat, sourceFormat)) {
            LOGGER.warning(() -> "Sound conversion not supported from "
                    + sourceFormat
                    + " to "
                    + targetFormat);
            return sourceStream;
        }

        return AudioSystem.getAudioInputStream(targetFormat, sourceStream);
    }

    private boolean isClipFriendlyFormat(AudioFormat format) {
        boolean signedPcm = format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED;
        boolean sixteenBit = format.getSampleSizeInBits() == 16;
        boolean littleEndian = !format.isBigEndian();

        return signedPcm && sixteenBit && littleEndian;
    }

    private void stopClip(Clip clip) {
        if (clip == null) {
            return;
        }

        clip.stop();
        clip.close();
    }

    private void applyVolume(Clip clip, double volume) {
        if (clip == null || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }

        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float decibels = volumeToDecibels(volume, gainControl.getMinimum(), gainControl.getMaximum());
        gainControl.setValue(decibels);
    }

    private float volumeToDecibels(double volume, float minimum, float maximum) {
        if (volume <= 0.0) {
            return minimum;
        }

        float decibels = (float) (20.0 * Math.log10(volume));
        return Math.max(minimum, Math.min(maximum, decibels));
    }

    private double clampVolume(double volume) {
        return Math.max(0.0, Math.min(1.0, volume));
    }

    private boolean isClipRunning(Clip clip) {
        return clip != null && clip.isRunning();
    }

    private boolean samePath(String first, String second) {
        if (isBlank(first) && isBlank(second)) {
            return true;
        }

        if (first == null) {
            return false;
        }

        return first.equals(second);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
