package org.main.battle;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Injectable random source shared by combat resolution and skill effects.
 */
public final class BattleRandom {
    private static final ThreadLocal<Source> OVERRIDE = new ThreadLocal<>();

    private BattleRandom() {
    }

    public static double nextDouble() {
        Source source = OVERRIDE.get();
        return source == null ? ThreadLocalRandom.current().nextDouble() : source.nextDouble();
    }

    public static int nextInt(int bound) {
        Source source = OVERRIDE.get();
        return source == null ? ThreadLocalRandom.current().nextInt(bound) : source.nextInt(bound);
    }

    public static Scope withSeed(long seed) {
        Source previous = OVERRIDE.get();
        Random random = new Random(seed);
        OVERRIDE.set(new Source() {
            @Override
            public double nextDouble() {
                return random.nextDouble();
            }

            @Override
            public int nextInt(int bound) {
                return random.nextInt(bound);
            }
        });
        return () -> {
            if (previous == null) OVERRIDE.remove();
            else OVERRIDE.set(previous);
        };
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    private interface Source {
        double nextDouble();

        int nextInt(int bound);
    }
}
