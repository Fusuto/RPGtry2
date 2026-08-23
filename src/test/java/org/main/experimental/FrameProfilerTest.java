package org.main.experimental;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameProfilerTest {
    @Test
    void benchmarkCountersReportAverageTotalAndMaximumRatherThanLastFrame() {
        FrameProfiler profiler = new FrameProfiler();
        profiler.beginFrame(0L);
        profiler.recordDraws(2, 6);
        profiler.recordUpload(100);
        profiler.recordCacheHit();
        profiler.endFrame(10_000_000L);

        profiler.beginFrame(10_000_000L);
        profiler.recordDraws(4, 12);
        profiler.recordUpload(300);
        profiler.recordCacheHit();
        profiler.recordCacheHit();
        profiler.recordCacheMiss();
        profiler.endFrame(30_000_000L);

        FrameProfiler.Snapshot snapshot = profiler.benchmarkSnapshot();
        assertEquals(3, snapshot.drawCalls());
        assertEquals(6, snapshot.totals().drawCalls());
        assertEquals(4, snapshot.maximums().drawCalls());
        assertEquals(200, snapshot.uploadedBytes());
        assertEquals(400, snapshot.totals().uploadedBytes());
        assertEquals(300, snapshot.maximums().uploadedBytes());
        assertEquals(3, snapshot.totals().cacheHits());
        assertEquals(1, snapshot.totals().cacheMisses());
    }
}
