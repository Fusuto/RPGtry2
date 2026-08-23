package org.main.core;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridges Java2D-authored UI layouts to the OpenGL pass that renders weapon
 * meshes directly into those screen rectangles.
 */
public final class ItemModelIconRenderQueue {
    private static final ThreadLocal<List<Request>> ACTIVE_CAPTURE = new ThreadLocal<>();

    private ItemModelIconRenderQueue() {
    }

    public static void beginCapture() {
        ACTIVE_CAPTURE.set(new ArrayList<>());
    }

    public static List<Request> finishCapture() {
        List<Request> requests = ACTIVE_CAPTURE.get();
        ACTIVE_CAPTURE.remove();
        return requests == null ? List.of() : List.copyOf(requests);
    }

    /** Returns true when the bitmap draw should be omitted for a live model icon. */
    public static boolean request(InventorySystem.Item item, int x, int y, int width, int height) {
        List<Request> requests = ACTIVE_CAPTURE.get();
        if (requests == null || item == null || !item.hasModelBackedIcon()
                || width <= 0 || height <= 0) {
            return false;
        }
        requests.add(new Request(
                item.getFirstPersonModelPath(),
                item.getModelIconProfile(),
                new Rectangle(x, y, width, height)));
        return true;
    }

    /**
     * Captures an icon rectangle after applying the Java2D transform used to
     * draw its containing window. The OpenGL icon pass operates in viewport
     * coordinates, so translated compact panels must use this overload.
     */
    public static boolean request(
            Graphics2D graphics,
            InventorySystem.Item item,
            int x,
            int y,
            int width,
            int height
    ) {
        if (graphics == null) {
            return request(item, x, y, width, height);
        }
        Rectangle transformed = graphics.getTransform()
                .createTransformedShape(new Rectangle(x, y, width, height))
                .getBounds();
        return request(item, transformed.x, transformed.y, transformed.width, transformed.height);
    }

    public record Request(
            String modelPath,
            ItemModelIconProfile profile,
            Rectangle bounds
    ) {
        public Request {
            modelPath = modelPath == null ? "" : modelPath.trim().replace('\\', '/');
            profile = profile == null ? ItemModelIconProfile.defaults() : profile;
            bounds = bounds == null ? new Rectangle() : new Rectangle(bounds);
        }
    }
}
