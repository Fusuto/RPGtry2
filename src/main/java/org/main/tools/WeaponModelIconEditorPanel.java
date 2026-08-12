package org.main.tools;

import org.joml.Matrix4d;
import org.joml.Vector4d;
import org.main.core.ItemModelIconProfile;
import org.main.experimental.LwjglStaticModel;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Authors the transform used by the live OpenGL inventory-model pass. */
final class WeaponModelIconEditorPanel extends JPanel {
    private static final double ROTATION_SENSITIVITY = 0.65;
    private static final double PAN_SENSITIVITY = 1.0 / 220.0;
    private final String modelPath;
    private ItemModelIconProfile profile;
    private LwjglStaticModel model;
    private String loadError = "";
    private boolean loading = true;
    private Point dragOrigin;
    private boolean panning;

    WeaponModelIconEditorPanel(String modelPath, ItemModelIconProfile initialProfile) {
        this.modelPath = modelPath == null ? "" : modelPath.trim().replace('\\', '/');
        this.profile = initialProfile == null ? ItemModelIconProfile.defaults() : initialProfile;
        setPreferredSize(new Dimension(520, 520));
        setMinimumSize(new Dimension(360, 360));
        setBackground(new Color(23, 25, 30));
        setToolTipText("Left-drag rotates, Alt-drag rolls, right/Shift-drag moves, and the wheel zooms.");
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                dragOrigin = event.getPoint();
                panning = SwingUtilities.isRightMouseButton(event) || event.isShiftDown();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragOrigin == null || model == null) return;
                int deltaX = event.getX() - dragOrigin.x;
                int deltaY = event.getY() - dragOrigin.y;
                if (panning || event.isShiftDown()) {
                    profile = profile.withOffset(
                            profile.offsetX() + deltaX * PAN_SENSITIVITY,
                            profile.offsetY() + deltaY * PAN_SENSITIVITY);
                } else if (event.isAltDown()) {
                    profile = profile.withRotation(
                            profile.rotationX(), profile.rotationY(),
                            profile.rotationZ() + deltaX * ROTATION_SENSITIVITY);
                } else {
                    profile = profile.withRotation(
                            profile.rotationX() + deltaY * ROTATION_SENSITIVITY,
                            profile.rotationY() + deltaX * ROTATION_SENSITIVITY,
                            profile.rotationZ());
                }
                dragOrigin = event.getPoint();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragOrigin = null;
                panning = false;
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                if (model == null) return;
                double multiplier = Math.pow(1.1, -event.getPreciseWheelRotation());
                profile = profile.withZoom(profile.zoom() * multiplier);
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        loadModel();
    }

    ItemModelIconProfile profile() {
        return profile;
    }

    void resetView() {
        profile = ItemModelIconProfile.defaults();
        repaint();
    }

    boolean hasUsableModel() {
        return model != null;
    }

    String loadError() {
        return loadError;
    }

    private void loadModel() {
        new SwingWorker<LwjglStaticModel, Void>() {
            @Override
            protected LwjglStaticModel doInBackground() throws Exception {
                return LwjglStaticModel.load(modelPath);
            }

            @Override
            protected void done() {
                loading = false;
                try {
                    model = get();
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    loadError = cause.getMessage() == null
                            ? cause.getClass().getSimpleName() : cause.getMessage();
                }
                repaint();
            }
        }.execute();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle icon = iconBounds();
            drawTransparencyGrid(g, icon);
            g.setClip(icon);
            if (model != null) {
                List<Triangle> triangles = triangles(icon);
                triangles.sort(Comparator.comparingDouble(Triangle::depth));
                drawSilhouetteOutline(g, icon, triangles);
                for (Triangle triangle : triangles) {
                    g.setColor(triangle.color());
                    g.fillPolygon(triangle.polygon());
                }
            }
            g.setClip(null);
            g.setColor(new Color(222, 190, 108));
            g.drawRoundRect(icon.x, icon.y, icon.width, icon.height, 10, 10);
            drawHelp(g, icon);
        } finally {
            g.dispose();
        }
    }

    private void drawSilhouetteOutline(Graphics2D g, Rectangle bounds, List<Triangle> triangles) {
        if (triangles.isEmpty()) return;
        BufferedImage silhouette = new BufferedImage(
                Math.max(1, bounds.width), Math.max(1, bounds.height), BufferedImage.TYPE_INT_ARGB);
        Graphics2D mask = silhouette.createGraphics();
        try {
            mask.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            mask.translate(-bounds.x, -bounds.y);
            float outlinePixels = Math.max(2.0f, bounds.width * (1.25f / 40.0f));
            mask.setStroke(new BasicStroke(outlinePixels * 2.0f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            mask.setColor(new Color(12, 10, 9, 245));
            for (Triangle triangle : triangles) {
                mask.fillPolygon(triangle.polygon());
                mask.drawPolygon(triangle.polygon());
            }
        } finally {
            mask.dispose();
        }
        g.drawImage(silhouette, bounds.x, bounds.y, null);
    }

    private Rectangle iconBounds() {
        int margin = 42;
        int size = Math.max(1, Math.min(getWidth(), getHeight()) - margin * 2);
        return new Rectangle((getWidth() - size) / 2, (getHeight() - size) / 2, size, size);
    }

    private void drawTransparencyGrid(Graphics2D g, Rectangle bounds) {
        int cell = 18;
        for (int y = bounds.y; y < bounds.y + bounds.height; y += cell) {
            for (int x = bounds.x; x < bounds.x + bounds.width; x += cell) {
                boolean alternate = ((x - bounds.x) / cell + (y - bounds.y) / cell) % 2 == 0;
                g.setColor(alternate ? new Color(66, 69, 76) : new Color(45, 48, 54));
                g.fillRect(x, y, Math.min(cell, bounds.x + bounds.width - x),
                        Math.min(cell, bounds.y + bounds.height - y));
            }
        }
    }

    private List<Triangle> triangles(Rectangle bounds) {
        Matrix4d rotation = rotation();
        ProjectedBounds projectedBounds = projectedBounds(rotation);
        double extent = Math.max(0.000001,
                Math.max(projectedBounds.width(), projectedBounds.height()));
        double scale = bounds.width * 0.78 * profile.zoom() / extent;
        double centerX = bounds.getCenterX() + profile.offsetX() * bounds.width * 0.42;
        double centerY = bounds.getCenterY() + profile.offsetY() * bounds.height * 0.42;
        List<Triangle> triangles = new ArrayList<>();
        for (LwjglStaticModel.Mesh mesh : model.meshes()) {
            int[] indices = mesh.indices();
            for (int index = 0; index + 2 < indices.length; index += 3) {
                ProjectedVertex a = project(mesh, indices[index], rotation, projectedBounds,
                        scale, centerX, centerY);
                ProjectedVertex b = project(mesh, indices[index + 1], rotation, projectedBounds,
                        scale, centerX, centerY);
                ProjectedVertex c = project(mesh, indices[index + 2], rotation, projectedBounds,
                        scale, centerX, centerY);
                Polygon polygon = new Polygon(
                        new int[]{a.x, b.x, c.x}, new int[]{a.y, b.y, c.y}, 3);
                triangles.add(new Triangle(polygon, (a.z + b.z + c.z) / 3.0,
                        triangleColor(mesh, indices[index], indices[index + 1], indices[index + 2])));
            }
        }
        return triangles;
    }

    private Matrix4d rotation() {
        return new Matrix4d()
                .rotateX(Math.toRadians(profile.rotationX()))
                .rotateY(Math.toRadians(profile.rotationY()))
                .rotateZ(Math.toRadians(profile.rotationZ()));
    }

    private ProjectedBounds projectedBounds(Matrix4d rotation) {
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        for (LwjglStaticModel.Mesh mesh : model.meshes()) {
            float[] positions = mesh.positions();
            for (int offset = 0; offset + 2 < positions.length; offset += 3) {
                Vector4d point = centered(positions, offset);
                rotation.transform(point);
                minimumX = Math.min(minimumX, point.x);
                minimumY = Math.min(minimumY, point.y);
                maximumX = Math.max(maximumX, point.x);
                maximumY = Math.max(maximumY, point.y);
            }
        }
        return new ProjectedBounds(minimumX, minimumY, maximumX, maximumY);
    }

    private ProjectedVertex project(
            LwjglStaticModel.Mesh mesh,
            int vertexIndex,
            Matrix4d rotation,
            ProjectedBounds projectedBounds,
            double scale,
            double centerX,
            double centerY
    ) {
        Vector4d point = centered(mesh.positions(), vertexIndex * 3);
        rotation.transform(point);
        return new ProjectedVertex(
                (int) Math.round(centerX + (point.x - projectedBounds.centerX()) * scale),
                (int) Math.round(centerY - (point.y - projectedBounds.centerY()) * scale),
                point.z);
    }

    private Vector4d centered(float[] positions, int offset) {
        return new Vector4d(
                positions[offset] - model.centerX(),
                positions[offset + 1] - model.centerY(),
                positions[offset + 2] - model.centerZ(), 1.0);
    }

    private Color triangleColor(LwjglStaticModel.Mesh mesh, int a, int b, int c) {
        BufferedImage texture = mesh.texture();
        if (texture == null || mesh.texCoords().length == 0) {
            return new Color(channel(mesh.red()), channel(mesh.green()),
                    channel(mesh.blue()), channel(mesh.alpha()));
        }
        double u = (mesh.texCoords()[a * 2] + mesh.texCoords()[b * 2]
                + mesh.texCoords()[c * 2]) / 3.0;
        double v = (mesh.texCoords()[a * 2 + 1] + mesh.texCoords()[b * 2 + 1]
                + mesh.texCoords()[c * 2 + 1]) / 3.0;
        int x = Math.max(0, Math.min(texture.getWidth() - 1,
                (int) Math.floor(fract(u) * texture.getWidth())));
        int y = Math.max(0, Math.min(texture.getHeight() - 1,
                (int) Math.floor(fract(v) * texture.getHeight())));
        Color sampled = new Color(texture.getRGB(x, y), true);
        return new Color(
                channel(sampled.getRed() / 255.0 * mesh.red()),
                channel(sampled.getGreen() / 255.0 * mesh.green()),
                channel(sampled.getBlue() / 255.0 * mesh.blue()),
                channel(sampled.getAlpha() / 255.0 * mesh.alpha()));
    }

    private void drawHelp(Graphics2D g, Rectangle icon) {
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 12f));
        g.setColor(Color.WHITE);
        String status;
        if (loading) {
            status = "Loading model...";
        } else if (model == null) {
            status = "Could not load model: " + loadError;
            g.setColor(new Color(255, 150, 150));
        } else {
            status = String.format(java.util.Locale.US,
                    "Rotation %.0f / %.0f / %.0f   Zoom %.2fx   Offset %.2f / %.2f",
                    profile.rotationX(), profile.rotationY(), profile.rotationZ(),
                    profile.zoom(), profile.offsetX(), profile.offsetY());
        }
        g.drawString(status, icon.x, Math.max(18, icon.y - 12));
        g.setColor(new Color(225, 225, 225));
        g.drawString("Left-drag: rotate   Alt-drag: roll   Right/Shift-drag: move   Wheel: zoom",
                icon.x, Math.min(getHeight() - 12, icon.y + icon.height + 24));
    }

    private static int channel(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value * 255.0)));
    }

    private static double fract(double value) {
        return value - Math.floor(value);
    }

    private record ProjectedBounds(
            double minimumX, double minimumY, double maximumX, double maximumY
    ) {
        private double width() { return maximumX - minimumX; }
        private double height() { return maximumY - minimumY; }
        private double centerX() { return (minimumX + maximumX) * 0.5; }
        private double centerY() { return (minimumY + maximumY) * 0.5; }
    }

    private record ProjectedVertex(int x, int y, double z) {
    }

    private record Triangle(Polygon polygon, double depth, Color color) {
    }
}
