package org.main.core;

import java.awt.Rectangle;
import java.util.List;

public final class PaperDollSliceLibrary {
    private static final int CANVAS_SIZE = 32;

    private PaperDollSliceLibrary() {
    }

    public static int canvasSize() {
        return CANVAS_SIZE;
    }

    public static List<Rectangle> masksFor(LimbSlot slot) {
        if (slot == null) {
            return List.of();
        }

        return switch (slot) {
            case HEAD -> List.of(new Rectangle(10, 0, 12, 10));
            case BODY -> List.of(new Rectangle(9, 10, 14, 10));
            case LEFT_ARM -> List.of(new Rectangle(5, 10, 6, 12));
            case RIGHT_ARM -> List.of(new Rectangle(21, 10, 6, 12));
            case LEGS -> List.of(new Rectangle(9, 20, 14, 12));
        };
    }
}
