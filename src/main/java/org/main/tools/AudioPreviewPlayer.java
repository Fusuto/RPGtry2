package org.main.tools;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Shared, race-safe lifecycle for the sound and song designer preview streams.
 */
final class AudioPreviewPlayer implements AutoCloseable {
    enum State {PLAYING, FINISHED}

    private final AtomicLong generation = new AtomicLong();
    private volatile SourceDataLine activeLine;

    void play(
            AudioFormat format,
            byte[] audioBytes,
            BooleanSupplier loop,
            int chunkSize,
            String threadName,
            Consumer<State> stateConsumer,
            Consumer<Exception> errorConsumer
    ) {
        stop();
        Objects.requireNonNull(format);
        byte[] bytes = audioBytes == null ? new byte[0] : audioBytes;
        BooleanSupplier shouldLoop = loop == null ? () -> false : loop;
        Consumer<State> states = stateConsumer == null ? ignored -> {
        } : stateConsumer;
        Consumer<Exception> errors = errorConsumer == null ? ignored -> {
        } : errorConsumer;
        long playback = generation.incrementAndGet();
        Thread thread = new Thread(() -> stream(
                playback, format, bytes, shouldLoop, Math.max(256, chunkSize), states, errors),
                threadName == null || threadName.isBlank() ? "audio-preview" : threadName);
        thread.setDaemon(true);
        thread.start();
    }

    void stop() {
        generation.incrementAndGet();
        SourceDataLine line = activeLine;
        activeLine = null;
        if (line != null) {
            line.stop();
            line.close();
        }
    }

    private void stream(
            long playback,
            AudioFormat format,
            byte[] bytes,
            BooleanSupplier loop,
            int chunkSize,
            Consumer<State> states,
            Consumer<Exception> errors
    ) {
        SourceDataLine line = null;
        try {
            line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
            line.open(format);
            if (generation.get() != playback) {
                return;
            }
            activeLine = line;
            line.start();
            states.accept(State.PLAYING);
            do {
                int offset = 0;
                while (generation.get() == playback && offset < bytes.length) {
                    int count = Math.min(chunkSize, bytes.length - offset);
                    offset += line.write(bytes, offset, count);
                }
            } while (generation.get() == playback && loop.getAsBoolean());
            if (generation.get() == playback) {
                line.drain();
                states.accept(State.FINISHED);
            }
        } catch (Exception error) {
            if (generation.get() == playback) {
                errors.accept(error);
            }
        } finally {
            if (activeLine == line) {
                activeLine = null;
            }
            if (line != null) {
                line.stop();
                line.close();
            }
        }
    }

    @Override
    public void close() {
        stop();
    }
}
