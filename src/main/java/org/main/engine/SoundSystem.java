package org.main.engine;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

public class SoundSystem {
    private static final double DEFAULT_VOLUME = 0.20;

    private Clip ambienceClip;
    private String ambiencePath;

    private Clip musicClip;
    private String musicPath;

    private Clip loopingSoundClip;
    private String loopingSoundPath;

    private double ambienceVolume = DEFAULT_VOLUME;
    private double musicVolume = DEFAULT_VOLUME;
    private double soundEffectVolume = DEFAULT_VOLUME;

    public void playSound(String soundPath) {
        if (isBlank(soundPath)) {
            return;
        }

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

    public void playAmbience(String soundPath) {
        if (samePath(ambiencePath, soundPath) && isClipRunning(ambienceClip)) {
            return;
        }

        stopAmbience();

        ambiencePath = soundPath;
        ambienceClip = loadLoopingClip(soundPath);
        applyVolume(ambienceClip, ambienceVolume);
    }

    public void stopAmbience() {
        stopClip(ambienceClip);
        ambienceClip = null;
        ambiencePath = null;
    }

    public void playMusic(String soundPath) {
        if (samePath(musicPath, soundPath) && isClipRunning(musicClip)) {
            return;
        }

        stopMusic();

        musicPath = soundPath;
        musicClip = loadLoopingClip(soundPath);
        applyVolume(musicClip, musicVolume);
    }

    public void stopMusic() {
        stopClip(musicClip);
        musicClip = null;
        musicPath = null;
    }

    public void stopAll() {
        stopAmbience();
        stopMusic();
        stopLoopingSound();
    }

    public double getAmbienceVolume() {
        return ambienceVolume;
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

    private Clip loadClip(String soundPath) {
        try {
            AudioInputStream audioInputStream = createPlayableAudioStream(soundPath);
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            return clip;
        } catch (Exception e) {
            System.out.println("Failed to load sound: " + soundPath);
            e.printStackTrace();
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
            System.out.println("Sound conversion not supported from "
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
