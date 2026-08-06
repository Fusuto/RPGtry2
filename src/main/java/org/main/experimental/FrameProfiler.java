package org.main.experimental;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/** Low-allocation rolling frame profiler shared by the game loop and viewport. */
final class FrameProfiler {
    enum Phase {
        INPUT,
        SIMULATION,
        SCENE_PREPARATION,
        TERRAIN_RENDERING,
        MODEL_RENDERING,
        BATTLE_RENDERING,
        HUD,
        BUFFER_SWAP
    }

    private static final int SAMPLE_COUNT = 600;
    private static final long HISTOGRAM_BUCKET_NANOS = 10_000L;
    private static final int HISTOGRAM_BUCKETS = 10_001;
    private final long[] frameNanos = new long[SAMPLE_COUNT];
    private final long[] frameAllocatedBytes = new long[SAMPLE_COUNT];
    private final long[] lifetimeHistogram = new long[HISTOGRAM_BUCKETS];
    private static final List<GarbageCollectorMXBean> GC_BEANS =
            List.copyOf(ManagementFactory.getGarbageCollectorMXBeans());
    private static final com.sun.management.ThreadMXBean ALLOCATION_BEAN = allocationBean();
    private final long[] currentPhases = new long[Phase.values().length];
    private final long[] phaseStarts = new long[Phase.values().length];
    private final long[] lifetimePhaseNanos = new long[Phase.values().length];
    private final long[] slowestFramePhases = new long[Phase.values().length];
    private int cursor;
    private int samples;
    private long frameStartNanos;
    private long frameStartAllocatedBytes;
    private long drawCalls;
    private long triangles;
    private long uploadedBytes;
    private long skinnedVertices;
    private long cacheHits;
    private long cacheMisses;
    private long lastGcCollections;
    private long gcEvents;
    private long gpuNanos;
    private long lifetimeFrames;
    private long lifetimeFrameNanos;
    private long lifetimeAllocatedBytes;
    private long lifetimeMaximumFrameNanos;

    FrameProfiler() {
        lastGcCollections = totalGcCollections();
    }

    void beginFrame(long nowNanos) {
        frameStartNanos = nowNanos;
        frameStartAllocatedBytes = threadAllocatedBytes();
        Arrays.fill(currentPhases, 0L);
        Arrays.fill(phaseStarts, 0L);
        drawCalls = 0;
        triangles = 0;
        uploadedBytes = 0;
        skinnedVertices = 0;
        cacheHits = 0;
        cacheMisses = 0;
    }

    void begin(Phase phase) {
        phaseStarts[phase.ordinal()] = System.nanoTime();
    }

    void end(Phase phase) {
        int index = phase.ordinal();
        long start = phaseStarts[index];
        if (start != 0L) {
            currentPhases[index] += Math.max(0L, System.nanoTime() - start);
            phaseStarts[index] = 0L;
        }
    }

    void recordPhase(Phase phase, long nanos) {
        currentPhases[phase.ordinal()] += Math.max(0L, nanos);
    }

    void recordDraw(long indexCount) {
        drawCalls++;
        triangles += Math.max(0L, indexCount / 3L);
    }

    void recordDraws(long count, long indexCount) {
        drawCalls += Math.max(0L, count);
        triangles += Math.max(0L, indexCount / 3L);
    }

    void recordUpload(long bytes) {
        uploadedBytes += Math.max(0L, bytes);
    }

    void recordSkinnedVertices(long count) {
        skinnedVertices += Math.max(0L, count);
    }

    void recordCacheHit() {
        cacheHits++;
    }

    void recordCacheMiss() {
        cacheMisses++;
    }

    void recordGpuNanos(long nanos) {
        gpuNanos = Math.max(0L, nanos);
    }

    void endFrame(long nowNanos) {
        long frameDuration = Math.max(1L, nowNanos - frameStartNanos);
        frameNanos[cursor] = frameDuration;
        long allocated = threadAllocatedBytes();
        long frameAllocation = allocated < 0L || frameStartAllocatedBytes < 0L
                ? 0L : Math.max(0L, allocated - frameStartAllocatedBytes);
        frameAllocatedBytes[cursor] = frameAllocation;
        lifetimeFrames++;
        lifetimeFrameNanos += frameDuration;
        lifetimeAllocatedBytes += frameAllocation;
        for (int index = 0; index < currentPhases.length; index++) {
            lifetimePhaseNanos[index] += currentPhases[index];
        }
        if (frameDuration > lifetimeMaximumFrameNanos) {
            lifetimeMaximumFrameNanos = frameDuration;
            System.arraycopy(currentPhases, 0, slowestFramePhases, 0, currentPhases.length);
        }
        int histogramIndex = (int) Math.min(HISTOGRAM_BUCKETS - 1,
                frameDuration / HISTOGRAM_BUCKET_NANOS);
        lifetimeHistogram[histogramIndex]++;
        cursor = (cursor + 1) % SAMPLE_COUNT;
        samples = Math.min(SAMPLE_COUNT, samples + 1);
        long collections = totalGcCollections();
        if (collections > lastGcCollections) {
            gcEvents += collections - lastGcCollections;
        }
        lastGcCollections = collections;
    }

    Snapshot snapshot() {
        if (samples == 0) {
            return Snapshot.EMPTY;
        }
        long[] ordered = Arrays.copyOf(frameNanos, samples);
        Arrays.sort(ordered);
        double sum = 0.0;
        long allocatedSum = 0L;
        for (long value : ordered) {
            sum += value;
        }
        for (int index = 0; index < samples; index++) {
            allocatedSum += frameAllocatedBytes[index];
        }
        double averageMs = sum / samples / 1_000_000.0;
        double onePercentLow = 1_000_000_000.0 / ordered[Math.max(0, (int) Math.ceil(samples * 0.99) - 1)];
        EnumMap<Phase, Double> phaseMs = new EnumMap<>(Phase.class);
        for (Phase phase : Phase.values()) {
            phaseMs.put(phase, currentPhases[phase.ordinal()] / 1_000_000.0);
        }
        return new Snapshot(
                averageMs,
                percentileMs(ordered, 0.50),
                percentileMs(ordered, 0.95),
                percentileMs(ordered, 0.99),
                ordered[ordered.length - 1] / 1_000_000.0,
                onePercentLow,
                drawCalls,
                triangles,
                uploadedBytes,
                skinnedVertices,
                cacheHits,
                cacheMisses,
                gcEvents,
                allocatedSum / Math.max(1L, samples),
                gpuNanos / 1_000_000.0,
                phaseMs,
                phaseMap(slowestFramePhases, 1.0)
        );
    }

    Snapshot benchmarkSnapshot() {
        if (lifetimeFrames == 0L) {
            return Snapshot.EMPTY;
        }
        long median = lifetimePercentileNanos(0.50);
        long p95 = lifetimePercentileNanos(0.95);
        long p99 = lifetimePercentileNanos(0.99);
        EnumMap<Phase, Double> phaseMs = phaseMap(
                lifetimePhaseNanos, 1.0 / Math.max(1L, lifetimeFrames));
        return new Snapshot(
                lifetimeFrameNanos / (double) lifetimeFrames / 1_000_000.0,
                median / 1_000_000.0,
                p95 / 1_000_000.0,
                p99 / 1_000_000.0,
                lifetimeMaximumFrameNanos / 1_000_000.0,
                1_000_000_000.0 / Math.max(1L, p99),
                drawCalls,
                triangles,
                uploadedBytes,
                skinnedVertices,
                cacheHits,
                cacheMisses,
                gcEvents,
                lifetimeAllocatedBytes / Math.max(1L, lifetimeFrames),
                gpuNanos / 1_000_000.0,
                phaseMs,
                phaseMap(slowestFramePhases, 1.0)
        );
    }

    void reset() {
        Arrays.fill(frameNanos, 0L);
        Arrays.fill(frameAllocatedBytes, 0L);
        Arrays.fill(lifetimeHistogram, 0L);
        Arrays.fill(currentPhases, 0L);
        Arrays.fill(phaseStarts, 0L);
        Arrays.fill(lifetimePhaseNanos, 0L);
        Arrays.fill(slowestFramePhases, 0L);
        cursor = 0;
        samples = 0;
        lifetimeFrames = 0L;
        lifetimeFrameNanos = 0L;
        lifetimeAllocatedBytes = 0L;
        lifetimeMaximumFrameNanos = 0L;
        gcEvents = 0L;
        lastGcCollections = totalGcCollections();
    }

    List<String> overlayLines() {
        Snapshot value = snapshot();
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT,
                "Frame %.2f ms avg | p95 %.2f | p99 %.2f | 1%% low %.0f FPS",
                value.averageMs(), value.p95Ms(), value.p99Ms(), value.onePercentLowFps()));
        lines.add("Draws " + value.drawCalls() + " | Triangles " + value.triangles()
                + " | Upload " + (value.uploadedBytes() / 1024L) + " KiB"
                + " | Skinned " + value.skinnedVertices());
        lines.add(String.format(Locale.ROOT,
                "Scene %.2f | Terrain %.2f | Models %.2f | Battle %.2f | HUD %.2f | Swap %.2f ms",
                value.phaseMs(Phase.SCENE_PREPARATION),
                value.phaseMs(Phase.TERRAIN_RENDERING),
                value.phaseMs(Phase.MODEL_RENDERING),
                value.phaseMs(Phase.BATTLE_RENDERING),
                value.phaseMs(Phase.HUD),
                value.phaseMs(Phase.BUFFER_SWAP)));
        lines.add(String.format(Locale.ROOT, "GPU %.2f ms", value.gpuTimeMs()));
        lines.add("Cache " + value.cacheHits() + "/" + value.cacheMisses()
                + " | Alloc " + (value.averageAllocatedBytes() / 1024L) + " KiB/f"
                + " | GC events " + value.gcEvents());
        return lines;
    }

    private static double percentileMs(long[] sorted, double percentile) {
        int index = Math.max(0, Math.min(sorted.length - 1,
                (int) Math.ceil(sorted.length * percentile) - 1));
        return sorted[index] / 1_000_000.0;
    }

    private long lifetimePercentileNanos(double percentile) {
        long target = Math.max(1L, (long) Math.ceil(lifetimeFrames * percentile));
        long accumulated = 0L;
        for (int index = 0; index < lifetimeHistogram.length; index++) {
            accumulated += lifetimeHistogram[index];
            if (accumulated >= target) {
                return Math.max(1L, (index + 1L) * HISTOGRAM_BUCKET_NANOS);
            }
        }
        return Math.max(1L, lifetimeMaximumFrameNanos);
    }

    private static EnumMap<Phase, Double> phaseMap(long[] values, double multiplier) {
        EnumMap<Phase, Double> result = new EnumMap<>(Phase.class);
        for (Phase phase : Phase.values()) {
            result.put(phase, values[phase.ordinal()] * multiplier / 1_000_000.0);
        }
        return result;
    }

    private static long totalGcCollections() {
        long total = 0;
        for (GarbageCollectorMXBean bean : GC_BEANS) {
            if (bean.getCollectionCount() > 0) {
                total += bean.getCollectionCount();
            }
        }
        return total;
    }

    private static long threadAllocatedBytes() {
        if (ALLOCATION_BEAN != null) {
            return ALLOCATION_BEAN.getThreadAllocatedBytes(Thread.currentThread().threadId());
        }
        return -1L;
    }

    private static com.sun.management.ThreadMXBean allocationBean() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean allocationBean)
                || !allocationBean.isThreadAllocatedMemorySupported()) {
            return null;
        }
        if (!allocationBean.isThreadAllocatedMemoryEnabled()) {
            try {
                allocationBean.setThreadAllocatedMemoryEnabled(true);
            } catch (SecurityException ignored) {
                return null;
            }
        }
        return allocationBean;
    }

    record Snapshot(
            double averageMs,
            double medianMs,
            double p95Ms,
            double p99Ms,
            double maximumMs,
            double onePercentLowFps,
            long drawCalls,
            long triangles,
            long uploadedBytes,
            long skinnedVertices,
            long cacheHits,
            long cacheMisses,
            long gcEvents,
            long averageAllocatedBytes,
            double gpuTimeMs,
            EnumMap<Phase, Double> phaseTimesMs,
            EnumMap<Phase, Double> slowestPhaseTimesMs
    ) {
        static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                new EnumMap<>(Phase.class), new EnumMap<>(Phase.class));

        double phaseMs(Phase phase) {
            return phaseTimesMs.getOrDefault(phase, 0.0);
        }

        double slowestPhaseMs(Phase phase) {
            return slowestPhaseTimesMs.getOrDefault(phase, 0.0);
        }
    }
}
