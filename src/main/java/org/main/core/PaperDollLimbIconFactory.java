package org.main.core;

import org.main.engine.AssetLoader;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates inventory icons directly from the same paper-doll regions used by
 * equipped grafts. No generated asset files are needed.
 */
public final class PaperDollLimbIconFactory {
    private static final int ICON_SIZE = 32;
    private static final int PADDING = 3;
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    private PaperDollLimbIconFactory() {
    }

    public static BufferedImage icon(String sourcePath, LimbSlot slot) {
        if (sourcePath == null || sourcePath.isBlank() || slot == null) {
            return null;
        }
        String key = sourcePath.trim().replace('\\', '/') + "|" + slot.name();
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(key, ignored -> createIcon(sourcePath, slot));
        }
    }

    private static BufferedImage createIcon(String sourcePath, LimbSlot slot) {
        BufferedImage source = AssetLoader.loadImage(sourcePath);
        List<Rectangle> masks = PaperDollSliceLibrary.masksFor(slot);
        if (source == null || masks.isEmpty()) {
            return null;
        }

        Rectangle bounds = new Rectangle(masks.getFirst());
        for (int i = 1; i < masks.size(); i++) {
            bounds = bounds.union(masks.get(i));
        }
        bounds = bounds.intersection(new Rectangle(0, 0, source.getWidth(), source.getHeight()));
        if (bounds.isEmpty()) {
            return null;
        }

        BufferedImage cropped = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D cropGraphics = cropped.createGraphics();
        for (Rectangle mask : masks) {
            Rectangle clipped = mask.intersection(bounds);
            if (clipped.isEmpty()) {
                continue;
            }
            cropGraphics.drawImage(
                    source,
                    clipped.x - bounds.x,
                    clipped.y - bounds.y,
                    clipped.x - bounds.x + clipped.width,
                    clipped.y - bounds.y + clipped.height,
                    clipped.x,
                    clipped.y,
                    clipped.x + clipped.width,
                    clipped.y + clipped.height,
                    null);
        }
        cropGraphics.dispose();

        BufferedImage icon = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        int available = ICON_SIZE - PADDING * 2;
        double scale = Math.min(available / (double) cropped.getWidth(), available / (double) cropped.getHeight());
        int width = Math.max(1, (int) Math.round(cropped.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(cropped.getHeight() * scale));
        int x = (ICON_SIZE - width) / 2;
        int y = (ICON_SIZE - height) / 2;
        Graphics2D graphics = icon.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(cropped, x, y, width, height, null);
        graphics.dispose();
        return icon;
    }
}
