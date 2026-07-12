package org.main.tools;

import org.main.engine.ApplicationPaths;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public class SpriteSheetSplitterTool extends JFrame {
    private static final Path DEFAULT_OUTPUT_FOLDER = ApplicationPaths.dataFolder()
            .resolve("images")
            .resolve("generated")
            .resolve("spritesheets");
    private static final Path PROJECT_FOLDER = ApplicationPaths.dataFolder()
            .resolve("tools")
            .resolve("spritesheets")
            .resolve("projects");

    private final SpriteCanvas canvas = new SpriteCanvas();
    private final RegionTableModel tableModel = new RegionTableModel();
    private final JTable regionTable = new JTable(tableModel);
    private final JLabel statusLabel = new JLabel("Open a sprite sheet to begin.");
    private final JTextField outputFolderField = new JTextField(DEFAULT_OUTPUT_FOLDER.toString(), 34);
    private final JSpinner zoomSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 12, 1));

    private BufferedImage spriteSheet;
    private Path spriteSheetPath;
    private Path outputFolder = DEFAULT_OUTPUT_FOLDER;
    private final List<SpriteRegion> regions = new ArrayList<>();
    private int selectedRegionIndex = -1;
    private int nextRegionNumber = 1;

    public SpriteSheetSplitterTool() {
        super("Sprite Sheet Splitter");

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setMinimumSize(new Dimension(1050, 700));

        add(createToolbar(), BorderLayout.NORTH);
        add(new JScrollPane(canvas), BorderLayout.CENTER);
        add(createSidePanel(), BorderLayout.EAST);
        add(createFooter(), BorderLayout.SOUTH);

        configureTable();
        installActions();

        pack();
        setLocationRelativeTo(null);
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton openButton = new JButton("Open Sheet");
        openButton.addActionListener(event -> openSpriteSheet());
        toolbar.add(openButton);

        JButton saveProjectButton = new JButton("Save Project");
        saveProjectButton.addActionListener(event -> saveProject());
        toolbar.add(saveProjectButton);

        JButton loadProjectButton = new JButton("Load Project");
        loadProjectButton.addActionListener(event -> loadProject());
        toolbar.add(loadProjectButton);

        toolbar.addSeparator();

        JButton deleteButton = new JButton("Delete Region");
        deleteButton.addActionListener(event -> deleteSelectedRegion());
        toolbar.add(deleteButton);

        JButton exportSelectedButton = new JButton("Export Selected");
        exportSelectedButton.addActionListener(event -> exportSelectedRegion());
        toolbar.add(exportSelectedButton);

        JButton exportAllButton = new JButton("Export All");
        exportAllButton.addActionListener(event -> exportAllRegions());
        toolbar.add(exportAllButton);

        toolbar.addSeparator();
        toolbar.add(new JLabel("Zoom"));
        toolbar.add(zoomSpinner);

        return toolbar;
    }

    private JPanel createSidePanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setPreferredSize(new Dimension(430, 600));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 8));

        JPanel outputPanel = new JPanel(new BorderLayout(4, 4));
        outputPanel.add(new JLabel("Output Folder"), BorderLayout.NORTH);
        outputPanel.add(outputFolderField, BorderLayout.CENTER);

        JButton chooseOutputButton = new JButton("Browse");
        chooseOutputButton.addActionListener(event -> chooseOutputFolder());
        outputPanel.add(chooseOutputButton, BorderLayout.EAST);

        panel.add(outputPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(regionTable), BorderLayout.CENTER);

        return panel;
    }

    private JPanel createFooter() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        panel.add(statusLabel, BorderLayout.CENTER);
        return panel;
    }

    private void configureTable() {
        regionTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        regionTable.getColumnModel().getColumn(0).setPreferredWidth(130);
        regionTable.getColumnModel().getColumn(1).setPreferredWidth(48);
        regionTable.getColumnModel().getColumn(2).setPreferredWidth(48);
        regionTable.getColumnModel().getColumn(3).setPreferredWidth(58);
        regionTable.getColumnModel().getColumn(4).setPreferredWidth(58);
        regionTable.getSelectionModel().addListSelectionListener(this::handleTableSelection);
    }

    private void installActions() {
        zoomSpinner.addChangeListener(event -> {
            canvas.setZoom((Integer) zoomSpinner.getValue());
            canvas.revalidate();
            canvas.repaint();
        });

        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_DELETE || event.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    deleteSelectedRegion();
                }
            }
        });
    }

    private void openSpriteSheet() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Images", "png"));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        loadSpriteSheet(chooser.getSelectedFile().toPath());
    }

    private void loadSpriteSheet(Path path) {
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                setStatus("Selected file was not a readable image.");
                return;
            }

            spriteSheet = image;
            spriteSheetPath = path.toAbsolutePath();
            regions.clear();
            selectedRegionIndex = -1;
            nextRegionNumber = 1;
            tableModel.fireTableDataChanged();
            canvas.revalidate();
            canvas.repaint();
            setStatus("Loaded " + spriteSheetPath.getFileName() + " (" + image.getWidth() + "x" + image.getHeight() + ").");
        } catch (Exception e) {
            setStatus("Failed to open sprite sheet: " + e.getMessage());
        }
    }

    private void chooseOutputFolder() {
        JFileChooser chooser = new JFileChooser(outputFolder.toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        outputFolder = chooser.getSelectedFile().toPath();
        outputFolderField.setText(outputFolder.toString());
    }

    private void handleTableSelection(ListSelectionEvent event) {
        if (event.getValueIsAdjusting()) {
            return;
        }

        selectedRegionIndex = regionTable.getSelectedRow();
        canvas.repaint();
    }

    private void selectRegion(int index) {
        selectedRegionIndex = index;
        if (index >= 0 && index < regions.size()) {
            regionTable.getSelectionModel().setSelectionInterval(index, index);
        } else {
            regionTable.clearSelection();
        }
        canvas.repaint();
    }

    private void addRegion(Rectangle bounds) {
        Rectangle clippedBounds = clipToImage(bounds);
        if (clippedBounds.width <= 0 || clippedBounds.height <= 0) {
            return;
        }

        SpriteRegion region = new SpriteRegion("sprite_" + nextRegionNumber++, clippedBounds);
        regions.add(region);
        tableModel.fireTableRowsInserted(regions.size() - 1, regions.size() - 1);
        selectRegion(regions.size() - 1);
        setStatus("Added " + region.name + ".");
    }

    private void deleteSelectedRegion() {
        if (selectedRegionIndex < 0 || selectedRegionIndex >= regions.size()) {
            return;
        }

        regions.remove(selectedRegionIndex);
        tableModel.fireTableDataChanged();
        selectRegion(Math.min(selectedRegionIndex, regions.size() - 1));
        setStatus("Deleted region.");
    }

    private void exportSelectedRegion() {
        if (selectedRegionIndex < 0 || selectedRegionIndex >= regions.size()) {
            setStatus("Select a region before exporting.");
            return;
        }

        exportRegions(List.of(regions.get(selectedRegionIndex)));
    }

    private void exportAllRegions() {
        if (regions.isEmpty()) {
            setStatus("No regions to export.");
            return;
        }

        exportRegions(regions);
    }

    private void exportRegions(List<SpriteRegion> regionsToExport) {
        if (spriteSheet == null) {
            setStatus("Open a sprite sheet before exporting.");
            return;
        }

        outputFolder = Path.of(outputFolderField.getText().trim());

        try {
            Files.createDirectories(outputFolder);

            int exported = 0;
            for (SpriteRegion region : regionsToExport) {
                Rectangle bounds = clipToImage(region.bounds);
                if (bounds.width <= 0 || bounds.height <= 0) {
                    continue;
                }

                BufferedImage cropped = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = cropped.createGraphics();
                g.drawImage(
                        spriteSheet,
                        0,
                        0,
                        bounds.width,
                        bounds.height,
                        bounds.x,
                        bounds.y,
                        bounds.x + bounds.width,
                        bounds.y + bounds.height,
                        null
                );
                g.dispose();

                String fileName = sanitizeFileName(region.name);
                if (fileName.isBlank()) {
                    fileName = "sprite_" + (exported + 1);
                }
                if (!fileName.endsWith(".png")) {
                    fileName += ".png";
                }

                ImageIO.write(cropped, "png", outputFolder.resolve(fileName).toFile());
                exported++;
            }

            setStatus("Exported " + exported + " PNG file(s) to " + outputFolder + ".");
        } catch (Exception e) {
            setStatus("Export failed: " + e.getMessage());
        }
    }

    private void saveProject() {
        try {
            Files.createDirectories(PROJECT_FOLDER);

            JFileChooser chooser = new JFileChooser(PROJECT_FOLDER.toFile());
            chooser.setFileFilter(new FileNameExtensionFilter("Sprite Splitter Project", "properties"));

            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            Path projectPath = ensureExtension(chooser.getSelectedFile().toPath(), ".properties");
            Properties properties = new Properties();
            properties.setProperty("source", spriteSheetPath == null ? "" : spriteSheetPath.toString());
            properties.setProperty("output", outputFolderField.getText().trim());
            properties.setProperty("region.count", Integer.toString(regions.size()));

            for (int i = 0; i < regions.size(); i++) {
                SpriteRegion region = regions.get(i);
                String prefix = "region." + i + ".";
                properties.setProperty(prefix + "name", region.name);
                properties.setProperty(prefix + "x", Integer.toString(region.bounds.x));
                properties.setProperty(prefix + "y", Integer.toString(region.bounds.y));
                properties.setProperty(prefix + "width", Integer.toString(region.bounds.width));
                properties.setProperty(prefix + "height", Integer.toString(region.bounds.height));
            }

            try (OutputStream stream = Files.newOutputStream(projectPath)) {
                properties.store(stream, "Sprite Sheet Splitter project");
            }

            setStatus("Saved project " + projectPath.getFileName() + ".");
        } catch (Exception e) {
            setStatus("Failed to save project: " + e.getMessage());
        }
    }

    private void loadProject() {
        try {
            JFileChooser chooser = new JFileChooser(PROJECT_FOLDER.toFile());
            chooser.setFileFilter(new FileNameExtensionFilter("Sprite Splitter Project", "properties"));

            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            Properties properties = new Properties();
            try (InputStream stream = Files.newInputStream(chooser.getSelectedFile().toPath())) {
                properties.load(stream);
            }

            Path source = Path.of(properties.getProperty("source", ""));
            if (!Files.exists(source)) {
                setStatus("Project source sheet was not found: " + source);
                return;
            }

            BufferedImage image = ImageIO.read(source.toFile());
            if (image == null) {
                setStatus("Project source sheet could not be read.");
                return;
            }

            spriteSheet = image;
            spriteSheetPath = source.toAbsolutePath();
            outputFolder = Path.of(properties.getProperty("output", DEFAULT_OUTPUT_FOLDER.toString()));
            outputFolderField.setText(outputFolder.toString());
            regions.clear();

            int count = parseInt(properties.getProperty("region.count"), 0);
            for (int i = 0; i < count; i++) {
                String prefix = "region." + i + ".";
                String name = properties.getProperty(prefix + "name", "sprite_" + (i + 1));
                int x = parseInt(properties.getProperty(prefix + "x"), 0);
                int y = parseInt(properties.getProperty(prefix + "y"), 0);
                int width = parseInt(properties.getProperty(prefix + "width"), 1);
                int height = parseInt(properties.getProperty(prefix + "height"), 1);
                regions.add(new SpriteRegion(name, clipToImage(new Rectangle(x, y, width, height))));
            }

            nextRegionNumber = regions.size() + 1;
            tableModel.fireTableDataChanged();
            selectRegion(regions.isEmpty() ? -1 : 0);
            canvas.revalidate();
            canvas.repaint();
            setStatus("Loaded project " + chooser.getSelectedFile().getName() + ".");
        } catch (Exception e) {
            setStatus("Failed to load project: " + e.getMessage());
        }
    }

    private Rectangle clipToImage(Rectangle rectangle) {
        if (spriteSheet == null || rectangle == null) {
            return new Rectangle();
        }

        int x = Math.max(0, Math.min(spriteSheet.getWidth(), rectangle.x));
        int y = Math.max(0, Math.min(spriteSheet.getHeight(), rectangle.y));
        int right = Math.max(0, Math.min(spriteSheet.getWidth(), rectangle.x + rectangle.width));
        int bottom = Math.max(0, Math.min(spriteSheet.getHeight(), rectangle.y + rectangle.height));
        return new Rectangle(x, y, Math.max(0, right - x), Math.max(0, bottom - y));
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private static Path ensureExtension(Path path, String extension) {
        String fileName = path.getFileName().toString();
        if (fileName.toLowerCase(Locale.ROOT).endsWith(extension)) {
            return path;
        }
        return path.resolveSibling(fileName + extension);
    }

    private static int parseInt(String rawValue, int fallback) {
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String sanitizeFileName(String rawName) {
        if (rawName == null) {
            return "";
        }

        return rawName
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SpriteSheetSplitterTool().setVisible(true));
    }

    private final class SpriteCanvas extends JPanel {
        private static final int HANDLE_SIZE = 7;

        private int zoom = 3;
        private Rectangle dragStartBounds;
        private Point dragStartImagePoint;
        private DragMode dragMode = DragMode.NONE;

        private SpriteCanvas() {
            setBackground(new Color(28, 28, 32));
            setFocusable(true);

            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    requestFocusInWindow();

                    if (spriteSheet == null) {
                        return;
                    }

                    Point imagePoint = screenToImage(event.getPoint());
                    int hitIndex = findRegionAt(imagePoint);
                    selectRegion(hitIndex);
                    dragStartImagePoint = imagePoint;

                    if (hitIndex >= 0) {
                        dragStartBounds = new Rectangle(regions.get(hitIndex).bounds);
                        dragMode = handleAt(imagePoint, dragStartBounds);
                    } else {
                        dragStartBounds = new Rectangle(imagePoint.x, imagePoint.y, 0, 0);
                        dragMode = DragMode.CREATE;
                    }
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (spriteSheet == null || dragMode == DragMode.NONE || dragStartImagePoint == null) {
                        return;
                    }

                    Point imagePoint = screenToImage(event.getPoint());

                    if (dragMode == DragMode.CREATE) {
                        Rectangle preview = rectangleFromPoints(dragStartImagePoint, imagePoint);
                        canvasPreviewBounds = clipToImage(preview);
                        repaint();
                        return;
                    }

                    if (selectedRegionIndex < 0 || selectedRegionIndex >= regions.size()) {
                        return;
                    }

                    Rectangle updated = new Rectangle(dragStartBounds);
                    int dx = imagePoint.x - dragStartImagePoint.x;
                    int dy = imagePoint.y - dragStartImagePoint.y;

                    switch (dragMode) {
                        case MOVE -> {
                            updated.x += dx;
                            updated.y += dy;
                        }
                        case RESIZE_EAST -> updated.width += dx;
                        case RESIZE_SOUTH -> updated.height += dy;
                        case RESIZE_WEST -> {
                            updated.x += dx;
                            updated.width -= dx;
                        }
                        case RESIZE_NORTH -> {
                            updated.y += dy;
                            updated.height -= dy;
                        }
                        case RESIZE_SOUTH_EAST -> {
                            updated.width += dx;
                            updated.height += dy;
                        }
                        default -> {
                        }
                    }

                    regions.get(selectedRegionIndex).bounds = normalizeMinimum(updated);
                    tableModel.fireTableRowsUpdated(selectedRegionIndex, selectedRegionIndex);
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    if (dragMode == DragMode.CREATE && canvasPreviewBounds != null) {
                        addRegion(canvasPreviewBounds);
                    }

                    canvasPreviewBounds = null;
                    dragStartBounds = null;
                    dragStartImagePoint = null;
                    dragMode = DragMode.NONE;
                    repaint();
                }
            };

            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
        }

        private Rectangle canvasPreviewBounds;

        private void setZoom(int zoom) {
            this.zoom = Math.max(1, zoom);
        }

        @Override
        public Dimension getPreferredSize() {
            if (spriteSheet == null) {
                return new Dimension(640, 480);
            }

            return new Dimension(spriteSheet.getWidth() * zoom, spriteSheet.getHeight() * zoom);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            if (spriteSheet == null) {
                g.setColor(new Color(210, 210, 220));
                g.drawString("Open a PNG sprite sheet, then drag boxes around sprites to export.", 24, 30);
                g.dispose();
                return;
            }

            drawCheckerboard(g);
            g.drawImage(spriteSheet, 0, 0, spriteSheet.getWidth() * zoom, spriteSheet.getHeight() * zoom, null);

            for (int i = 0; i < regions.size(); i++) {
                drawRegion(g, regions.get(i).bounds, i == selectedRegionIndex, regions.get(i).name);
            }

            if (canvasPreviewBounds != null && !canvasPreviewBounds.isEmpty()) {
                drawRegion(g, canvasPreviewBounds, true, "new");
            }

            g.dispose();
        }

        private void drawCheckerboard(Graphics2D g) {
            int tile = 8 * zoom;
            int width = spriteSheet.getWidth() * zoom;
            int height = spriteSheet.getHeight() * zoom;

            for (int y = 0; y < height; y += tile) {
                for (int x = 0; x < width; x += tile) {
                    boolean dark = ((x / tile) + (y / tile)) % 2 == 0;
                    g.setColor(dark ? new Color(54, 54, 60) : new Color(72, 72, 78));
                    g.fillRect(x, y, tile, tile);
                }
            }
        }

        private void drawRegion(Graphics2D g, Rectangle bounds, boolean selected, String label) {
            Rectangle screenBounds = imageToScreen(bounds);
            g.setStroke(new BasicStroke(selected ? 2f : 1f));
            g.setColor(selected ? new Color(255, 222, 97) : new Color(78, 198, 255));
            g.drawRect(screenBounds.x, screenBounds.y, screenBounds.width, screenBounds.height);

            if (selected) {
                g.setColor(new Color(255, 222, 97, 120));
                g.fillRect(screenBounds.x, screenBounds.y, screenBounds.width, screenBounds.height);
                g.setColor(new Color(20, 20, 20));
                g.fillRect(screenBounds.x + screenBounds.width - HANDLE_SIZE, screenBounds.y + screenBounds.height - HANDLE_SIZE, HANDLE_SIZE, HANDLE_SIZE);
            }

            if (label != null && !label.isBlank()) {
                g.setColor(new Color(0, 0, 0, 170));
                g.fillRect(screenBounds.x, Math.max(0, screenBounds.y - 16), Math.max(44, label.length() * 7 + 8), 16);
                g.setColor(Color.WHITE);
                g.drawString(label, screenBounds.x + 4, Math.max(12, screenBounds.y - 4));
            }
        }

        private Point screenToImage(Point point) {
            return new Point(
                    Math.max(0, Math.min(spriteSheet.getWidth(), point.x / zoom)),
                    Math.max(0, Math.min(spriteSheet.getHeight(), point.y / zoom))
            );
        }

        private Rectangle imageToScreen(Rectangle rectangle) {
            return new Rectangle(rectangle.x * zoom, rectangle.y * zoom, rectangle.width * zoom, rectangle.height * zoom);
        }

        private int findRegionAt(Point imagePoint) {
            for (int i = regions.size() - 1; i >= 0; i--) {
                if (regions.get(i).bounds.contains(imagePoint)) {
                    return i;
                }
            }
            return -1;
        }

        private DragMode handleAt(Point imagePoint, Rectangle bounds) {
            int margin = Math.max(2, HANDLE_SIZE / zoom + 1);
            boolean nearLeft = Math.abs(imagePoint.x - bounds.x) <= margin;
            boolean nearRight = Math.abs(imagePoint.x - (bounds.x + bounds.width)) <= margin;
            boolean nearTop = Math.abs(imagePoint.y - bounds.y) <= margin;
            boolean nearBottom = Math.abs(imagePoint.y - (bounds.y + bounds.height)) <= margin;

            if (nearRight && nearBottom) {
                return DragMode.RESIZE_SOUTH_EAST;
            }
            if (nearRight) {
                return DragMode.RESIZE_EAST;
            }
            if (nearBottom) {
                return DragMode.RESIZE_SOUTH;
            }
            if (nearLeft) {
                return DragMode.RESIZE_WEST;
            }
            if (nearTop) {
                return DragMode.RESIZE_NORTH;
            }
            return DragMode.MOVE;
        }

        private Rectangle rectangleFromPoints(Point a, Point b) {
            int x = Math.min(a.x, b.x);
            int y = Math.min(a.y, b.y);
            int width = Math.abs(a.x - b.x);
            int height = Math.abs(a.y - b.y);
            return new Rectangle(x, y, width, height);
        }

        private Rectangle normalizeMinimum(Rectangle rectangle) {
            Rectangle normalized = new Rectangle(rectangle);
            if (normalized.width < 1) {
                normalized.width = 1;
            }
            if (normalized.height < 1) {
                normalized.height = 1;
            }
            return clipToImage(normalized);
        }
    }

    private final class RegionTableModel extends AbstractTableModel {
        private final String[] columns = {"Name", "X", "Y", "W", "H"};

        @Override
        public int getRowCount() {
            return regions.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            SpriteRegion region = regions.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> region.name;
                case 1 -> region.bounds.x;
                case 2 -> region.bounds.y;
                case 3 -> region.bounds.width;
                case 4 -> region.bounds.height;
                default -> "";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= regions.size()) {
                return;
            }

            SpriteRegion region = regions.get(rowIndex);
            switch (columnIndex) {
                case 0 -> region.name = String.valueOf(value);
                case 1 -> region.bounds.x = parseInt(String.valueOf(value), region.bounds.x);
                case 2 -> region.bounds.y = parseInt(String.valueOf(value), region.bounds.y);
                case 3 -> region.bounds.width = Math.max(1, parseInt(String.valueOf(value), region.bounds.width));
                case 4 -> region.bounds.height = Math.max(1, parseInt(String.valueOf(value), region.bounds.height));
                default -> {
                }
            }

            region.bounds = clipToImage(region.bounds);
            fireTableRowsUpdated(rowIndex, rowIndex);
            canvas.repaint();
        }
    }

    private static final class SpriteRegion {
        private String name;
        private Rectangle bounds;

        private SpriteRegion(String name, Rectangle bounds) {
            this.name = name;
            this.bounds = bounds;
        }
    }

    private enum DragMode {
        NONE,
        CREATE,
        MOVE,
        RESIZE_EAST,
        RESIZE_SOUTH,
        RESIZE_WEST,
        RESIZE_NORTH,
        RESIZE_SOUTH_EAST
    }
}
