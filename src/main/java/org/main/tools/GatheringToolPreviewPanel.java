package org.main.tools;

import org.main.experimental.LwjglStaticModel;

import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class GatheringToolPreviewPanel extends JPanel {
    private static final long CYCLE_MS = 4_000L;

    private final Map<String, JSpinner> controls;
    private final String displayName;
    private final String modelPath;
    private final String configurationPrefix;
    private final Map<LwjglStaticModel.Mesh, Color> meshColors = new IdentityHashMap<>();
    private final long startedAtMs = System.currentTimeMillis();
    private final Timer timer;
    private LwjglStaticModel model;
    private String loadError = "";

    GatheringToolPreviewPanel(
            String displayName,
            String modelPath,
            String configurationPrefix,
            Map<String, JSpinner> controls
    ) {
        this.displayName = displayName;
        this.modelPath = modelPath;
        this.configurationPrefix = configurationPrefix;
        this.controls = controls;
        setPreferredSize(new Dimension(620, 520));
        setMinimumSize(new Dimension(420, 320));
        setBackground(new Color(13, 14, 22));
        try {
            model = LwjglStaticModel.load(modelPath);
            for (LwjglStaticModel.Mesh mesh : model.meshes()) {
                meshColors.put(mesh, representativeColor(mesh));
            }
        } catch (IOException | RuntimeException exception) {
            loadError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
        controls.values().forEach(spinner -> spinner.addChangeListener(event -> repaint()));
        timer = new Timer(33, event -> repaint());
        timer.start();
    }

    void stopPreview() {
        timer.stop();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawBackdrop(g);
            if (model == null) {
                g.setColor(new Color(255, 150, 150));
                g.drawString("Unable to load pickaxe preview: " + loadError, 20, 34);
                return;
            }
            AnimationFrame frame = animationFrame();
            List<PreviewTriangle> triangles = transformedTriangles(frame);
            triangles.sort(Comparator.comparingDouble(PreviewTriangle::depth));
            for (PreviewTriangle triangle : triangles) {
                g.setColor(triangle.color());
                g.fillPolygon(triangle.polygon());
                g.setColor(new Color(0, 0, 0, 48));
                g.drawPolygon(triangle.polygon());
            }
            drawCaption(g, frame);
        } finally {
            g.dispose();
        }
    }

    private void drawBackdrop(Graphics2D g) {
        int width = getWidth();
        int height = getHeight();
        g.setColor(new Color(20, 24, 36));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(44, 48, 59));
        g.fillRect(0, (int) (height * 0.68), width, height);
        g.setColor(new Color(70, 72, 80));
        g.setStroke(new BasicStroke(1f));
        for (int y = (int) (height * 0.68); y < height; y += 22) {
            g.drawLine(0, y, width, y);
        }
        g.setColor(new Color(42, 31, 22));
        g.fillOval(width / 2 - 115, height / 2 - 40, 230, 165);
        g.setColor(new Color(91, 66, 42));
        g.drawOval(width / 2 - 115, height / 2 - 40, 230, 165);
        g.setColor(new Color(255, 255, 255, 24));
        g.drawLine(width / 2, 0, width / 2, height);
        g.drawLine(0, height / 2, width, height / 2);
    }

    private void drawCaption(Graphics2D g, AnimationFrame frame) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRoundRect(12, 12, 250, 54, 10, 10);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
        g.drawString(displayName + " Preview", 24, 34);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 12f));
        g.setColor(frame.success() ? new Color(125, 235, 150) : new Color(245, 185, 100));
        g.drawString(frame.label() + " - loops success and failure", 24, 54);
    }

    private AnimationFrame animationFrame() {
        long elapsed = Math.max(0L, System.currentTimeMillis() - startedAtMs);
        long cycleIndex = elapsed / CYCLE_MS;
        long localMs = elapsed % CYCLE_MS;
        boolean success = cycleIndex % 2L == 0L;
        double windup = value("windupDegrees", 25.0);
        double endpoint = value(success ? "successDegrees" : "failureDegrees", success ? -55.0 : -20.0);
        double reach = value(success ? "successPenetration" : "failurePenetration", success ? 0.32 : 0.10);
        if (localMs < 1_000L) {
            return new AnimationFrame(0.0, 0.0, success, "Rest");
        }
        if (localMs < 1_350L) {
            double progress = smooth((localMs - 1_000L) / 350.0);
            return new AnimationFrame(lerp(0.0, windup, progress), 0.0, success, "Wind up");
        }
        if (localMs < 1_700L) {
            double progress = smooth((localMs - 1_350L) / 350.0);
            return new AnimationFrame(lerp(windup, endpoint, progress), reach * progress, success, success ? "Success strike" : "Failure strike");
        }
        if (localMs < 2_050L) {
            double progress = smooth((localMs - 1_700L) / 350.0);
            return new AnimationFrame(lerp(endpoint, 0.0, progress), lerp(reach, 0.0, progress), success, "Recovery");
        }
        return new AnimationFrame(0.0, 0.0, success, "Rest");
    }

    private List<PreviewTriangle> transformedTriangles(AnimationFrame frame) {
        List<PreviewTriangle> triangles = new ArrayList<>();
        double scale = model.normalizedScaleForHeight(Math.max(0.05, value("height", 0.76)));
        double axisX = value("swingAxisX", 0.0);
        double axisY = value("swingAxisY", 0.0);
        double axisZ = value("swingAxisZ", 1.0);
        if (Math.abs(axisX) + Math.abs(axisY) + Math.abs(axisZ) < 0.0001) {
            axisZ = 1.0;
        }
        Transform transform = new Transform(
                value("positionX", 0.42),
                value("positionY", -0.46),
                value("positionZ", -0.92) - frame.penetration(),
                value("rotationX", -18.0),
                value("rotationY", 0.0),
                value("rotationZ", -24.0),
                axisX,
                axisY,
                axisZ,
                frame.angleDegrees(),
                scale
        );
        for (LwjglStaticModel.Mesh mesh : model.meshes()) {
            float[] positions = mesh.positions();
            int[] indices = mesh.indices();
            for (int offset = 0; offset + 2 < indices.length; offset += 3) {
                Vec3 a = transform.apply(vertex(positions, indices[offset]));
                Vec3 b = transform.apply(vertex(positions, indices[offset + 1]));
                Vec3 c = transform.apply(vertex(positions, indices[offset + 2]));
                if (a.z() >= -0.05 || b.z() >= -0.05 || c.z() >= -0.05) {
                    continue;
                }
                Polygon polygon = new Polygon();
                addProjectedPoint(polygon, a);
                addProjectedPoint(polygon, b);
                addProjectedPoint(polygon, c);
                Color base = meshColors.getOrDefault(mesh, Color.LIGHT_GRAY);
                double shade = triangleShade(a, b, c);
                Color shaded = new Color(
                        clampColor(base.getRed() * shade),
                        clampColor(base.getGreen() * shade),
                        clampColor(base.getBlue() * shade),
                        base.getAlpha()
                );
                triangles.add(new PreviewTriangle(polygon, (a.z() + b.z() + c.z()) / 3.0, shaded));
            }
        }
        return triangles;
    }

    private void addProjectedPoint(Polygon polygon, Vec3 point) {
        double focal = Math.max(100.0, getHeight() * 0.90);
        double inverseDepth = 1.0 / -point.z();
        polygon.addPoint(
                (int) Math.round(getWidth() * 0.5 + point.x() * inverseDepth * focal),
                (int) Math.round(getHeight() * 0.5 - point.y() * inverseDepth * focal)
        );
    }

    private double triangleShade(Vec3 a, Vec3 b, Vec3 c) {
        Vec3 ab = b.subtract(a);
        Vec3 ac = c.subtract(a);
        Vec3 normal = ab.cross(ac);
        double length = Math.sqrt(normal.x() * normal.x() + normal.y() * normal.y() + normal.z() * normal.z());
        return length < 0.000001 ? 0.7 : 0.48 + 0.52 * Math.abs(normal.z()) / length;
    }

    private Vec3 vertex(float[] positions, int index) {
        int offset = index * 3;
        return new Vec3(
                (positions[offset] - model.centerX()),
                (positions[offset + 1] - model.baseY()),
                (positions[offset + 2] - model.centerZ())
        );
    }

    private double value(String suffix, double fallback) {
        JSpinner spinner = controls.get(configurationPrefix + ".viewModel." + suffix);
        return spinner == null ? fallback : ((Number) spinner.getValue()).doubleValue();
    }

    private Color representativeColor(LwjglStaticModel.Mesh mesh) {
        double red = mesh.red();
        double green = mesh.green();
        double blue = mesh.blue();
        BufferedImage texture = mesh.texture();
        if (texture != null && texture.getWidth() > 0 && texture.getHeight() > 0) {
            long totalRed = 0L;
            long totalGreen = 0L;
            long totalBlue = 0L;
            long count = 0L;
            int stepX = Math.max(1, texture.getWidth() / 32);
            int stepY = Math.max(1, texture.getHeight() / 32);
            for (int y = 0; y < texture.getHeight(); y += stepY) {
                for (int x = 0; x < texture.getWidth(); x += stepX) {
                    Color sample = new Color(texture.getRGB(x, y), true);
                    if (sample.getAlpha() < 16) {
                        continue;
                    }
                    totalRed += sample.getRed();
                    totalGreen += sample.getGreen();
                    totalBlue += sample.getBlue();
                    count++;
                }
            }
            if (count > 0L) {
                red *= totalRed / (255.0 * count);
                green *= totalGreen / (255.0 * count);
                blue *= totalBlue / (255.0 * count);
            }
        }
        return new Color(
                clampColor(red * 255.0),
                clampColor(green * 255.0),
                clampColor(blue * 255.0),
                clampColor(mesh.alpha() * 255.0)
        );
    }

    private static int clampColor(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    private static double smooth(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    private static Vec3 rotate(Vec3 point, double degrees, double axisX, double axisY, double axisZ) {
        double length = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
        if (length < 0.000001 || Math.abs(degrees) < 0.000001) {
            return point;
        }
        double x = axisX / length;
        double y = axisY / length;
        double z = axisZ / length;
        double radians = Math.toRadians(degrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double dot = point.x() * x + point.y() * y + point.z() * z;
        return new Vec3(
                point.x() * cosine + (y * point.z() - z * point.y()) * sine + x * dot * (1.0 - cosine),
                point.y() * cosine + (z * point.x() - x * point.z()) * sine + y * dot * (1.0 - cosine),
                point.z() * cosine + (x * point.y() - y * point.x()) * sine + z * dot * (1.0 - cosine)
        );
    }

    private record AnimationFrame(double angleDegrees, double penetration, boolean success, String label) {
    }

    private record PreviewTriangle(Polygon polygon, double depth, Color color) {
    }

    private record Vec3(double x, double y, double z) {
        private Vec3 subtract(Vec3 other) {
            return new Vec3(x - other.x, y - other.y, z - other.z);
        }

        private Vec3 cross(Vec3 other) {
            return new Vec3(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x
            );
        }
    }

    private record Transform(
            double positionX,
            double positionY,
            double positionZ,
            double rotationX,
            double rotationY,
            double rotationZ,
            double swingAxisX,
            double swingAxisY,
            double swingAxisZ,
            double swingDegrees,
            double scale
    ) {
        private Vec3 apply(Vec3 source) {
            Vec3 point = new Vec3(source.x * scale, source.y * scale, source.z * scale);
            point = rotate(point, swingDegrees, swingAxisX, swingAxisY, swingAxisZ);
            point = rotate(point, rotationZ, 0.0, 0.0, 1.0);
            point = rotate(point, rotationY, 0.0, 1.0, 0.0);
            point = rotate(point, rotationX, 1.0, 0.0, 0.0);
            return new Vec3(point.x + positionX, point.y + positionY, point.z + positionZ);
        }
    }
}
