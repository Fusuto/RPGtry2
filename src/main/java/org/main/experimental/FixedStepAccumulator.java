package org.main.experimental;

import java.util.function.IntConsumer;

/** Deterministic nanosecond accumulator for the 60 Hz gameplay clock. */
final class FixedStepAccumulator {
    private final long stepNanos;
    private final int maximumCatchUpSteps;
    private long accumulatedNanos;
    private long tick;

    FixedStepAccumulator(long stepNanos, int maximumCatchUpSteps) {
        this.stepNanos = Math.max(1L, stepNanos);
        this.maximumCatchUpSteps = Math.max(1, maximumCatchUpSteps);
    }

    Result advance(long elapsedNanos, IntConsumer update) {
        accumulatedNanos += Math.max(0L, elapsedNanos);
        int steps = 0;
        while (accumulatedNanos >= stepNanos && steps < maximumCatchUpSteps) {
            // The runtime currently accepts integral milliseconds. Two 17 ms
            // steps plus one 16 ms step preserve a 50 ms/three-tick cadence.
            int deltaMs = tick++ % 3L == 2L ? 16 : 17;
            update.accept(deltaMs);
            accumulatedNanos -= stepNanos;
            steps++;
        }
        boolean dropped = accumulatedNanos >= stepNanos;
        if (dropped) {
            accumulatedNanos %= stepNanos;
        }
        return new Result(steps, dropped, accumulatedNanos / (double) stepNanos);
    }

    record Result(int steps, boolean droppedExcessTime, double interpolationAlpha) {
    }
}
