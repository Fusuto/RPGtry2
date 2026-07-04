package org.main;

import org.main.engine.*;
import org.main.monsters.Monster;
import org.main.monsters.MonsterType;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WizardryBase extends JPanel implements KeyListener {
    private static final int TILE_EMPTY = 0;
    private static final int TILE_WALL = 1;

    private final DungeonRenderer dungeonRenderer = new DungeonRenderer();
    private final MovementEngine movementEngine = new MovementEngine();

    private final DungeonMap dungeonMap = DungeonMap.testMap();

    private int playerX = 1;
    private int playerY = 1;

    // 0 = north, 1 = east, 2 = south, 3 = west
    private int dir = 1;

    private final List<MapEntity> entities = new ArrayList<MapEntity>();
    private final TextureManager textureManager = new TextureManager();
    private long lastUpdateTime = System.currentTimeMillis();

    public WizardryBase() {
        setPreferredSize(new Dimension(900, 600));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);

        textureManager.loadFromFolder("src/main/java/org/main/images/building");

        dungeonRenderer.setTextureManager(textureManager);
        dungeonRenderer.setWallTextureTheme("wall", "brick", "stone");
        dungeonRenderer.setFloorTextureTheme("floor","wood","planks","wide");

//        SpriteAnimation slimeIdle = SpriteAnimation.fromSpriteSheet(
//                "src/main/java/org/main/images/monster/RPG Maker MV format Animated Sideview Battlers/Slime/Slime1_1.png",
//                0,      // startX
//                0,      // startY
//                64,     // frame width
//                64,     // frame height
//                4,      // number of idle frames
//                180     // milliseconds per frame
//        );
//
//        entities.add(new MapEntity(
//                "Test Slime",
//                EntityType.ENEMY,
//                4,
//                3,
//                slimeIdle
//        ));
        BufferedImage slimeImage = loadImage("src/main/java/org/main/images/monster/Frontview Batch Battlers/Slime Blue.png");

        entities.add(new MapEntity(
                "Test Slime",
                EntityType.ENEMY,
                4,
                3,
                slimeImage
        ));
//        entities.add(new MapEntity(new Monster(MonsterType.SLIME), 4, 3));
        Timer timer = new Timer(16, e -> {
            long now = System.currentTimeMillis();
            int deltaMs = (int) (now - lastUpdateTime);
            lastUpdateTime = now;

            updateGame(deltaMs);
            repaint();
        });

        timer.start();
    }
    private void updateGame(int deltaMs) {
        for (MapEntity entity : entities) {
            entity.update(deltaMs);
        }
    }

    private boolean isPlayerAt(int x, int y) {
        return playerX == x && playerY == y;
    }

    private MapEntity getBlockingEntityAt(int x, int y) {
        for (MapEntity entity : entities) {
            if (entity.isAt(x, y) && entity.blocksMovement()) {
                return entity;
            }
        }

        return null;
    }

    private boolean isOccupied(int x, int y) {
        return isPlayerAt(x, y) || getBlockingEntityAt(x, y) != null;
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Wizardry-esque Dungeon Base");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new WizardryBase());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

//        dungeonRenderer.draw(g2, dungeonMap, playerX, playerY, dir, getWidth(), getHeight());
        dungeonRenderer.draw(g2, dungeonMap, entities, playerX, playerY, dir, getWidth(), getHeight());
        drawMiniMap(g2);
        drawHud(g2);
    }

    private void drawMiniMap(Graphics2D g) {
        int tile = 18;
        int startX = 20;
        int startY = 20;

        for (int y = 0; y < dungeonMap.getHeight(); y++) {
            for (int x = 0; x < dungeonMap.getWidth(); x++) {
                TileType tileType = dungeonMap.getTile(x, y);

                g.setColor(tileType.isWallLike() ? Color.DARK_GRAY : Color.GRAY);
                g.fillRect(startX + x * tile, startY + y * tile, tile - 2, tile - 2);
            }
        }

        for (MapEntity entity : entities) {
            g.setColor(entity.getType() == EntityType.ENEMY ? Color.MAGENTA : Color.CYAN);

            g.fillOval(
                    startX + entity.getX() * tile + 4,
                    startY + entity.getY() * tile + 4,
                    tile - 10,
                    tile - 10
            );
        }

        g.setColor(Color.RED);
        g.fillOval(startX + playerX * tile + 3, startY + playerY * tile + 3, tile - 8, tile - 8);
    }

    private BufferedImage loadImage(String path) {
        try {
            return ImageIO.read(new File(path));
        } catch (IOException e) {
            System.out.println("Failed to load image: " + path);
            e.printStackTrace();
            return null;
        }
    }

    private void drawHud(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.drawString("W / Numpad 8: Move Forward", 20, getHeight() - 90);
        g.drawString("S / Numpad 2: Move Backward", 20, getHeight() - 70);
        g.drawString("A/D / Numpad 4/6: Strafe", 20, getHeight() - 50);
        g.drawString("Q/E / Numpad 7/9: Turn", 20, getHeight() - 30);
        g.drawString("F / Enter / Space: Interact", 20, getHeight() - 10);
    }

    private void movement(int dx, int dy) {
        Point nextPosition = movementEngine.move(playerX, playerY, dx, dy, dungeonMap);

        // If the map blocked us, nothing changed.
        if (nextPosition.x == playerX && nextPosition.y == playerY) {
            return;
        }

        // If an enemy/NPC/chest is there, also block movement.
        if (getBlockingEntityAt(nextPosition.x, nextPosition.y) != null) {
            return;
        }

        playerX = nextPosition.x;
        playerY = nextPosition.y;
    }

    private void turnLeft() {
        dir = movementEngine.turnLeft(dir);
    }

    private void turnRight() {
        dir = movementEngine.turnRight(dir);
    }
    private int forwardX() {
        return movementEngine.forwardX(dir);
    }

    private int forwardY() {
        return movementEngine.forwardY(dir);
    }

    private int leftX() {
        return movementEngine.leftX(dir);
    }

    private int leftY() {
        return movementEngine.leftY(dir);
    }

    private int rightX() {
        return movementEngine.rightX(dir);
    }

    private int rightY() {
        return movementEngine.rightY(dir);
    }

    private String directionName() {
        return movementEngine.directionName(dir);
    }

    private void interact() {
        int targetX = playerX + forwardX();
        int targetY = playerY + forwardY();

        TileType targetTile = dungeonMap.getTile(targetX, targetY);

        if (targetTile == TileType.DOOR_CLOSED) {
            dungeonMap.setTile(targetX, targetY, TileType.DOOR_OPEN);
            return;
        }

        if (targetTile == TileType.DOOR_OPEN) {
            if (isOccupied(targetX, targetY)) {
                System.out.println("Something is blocking the door.");
                return;
            }

            dungeonMap.setTile(targetX, targetY, TileType.DOOR_CLOSED);
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            //Forward
            case KeyEvent.VK_W, KeyEvent.VK_NUMPAD8 -> movement(forwardX(), forwardY());
            //Strife Left
            case KeyEvent.VK_A, KeyEvent.VK_NUMPAD4 -> movement(leftX(), leftY());
            //Strife Right
            case KeyEvent.VK_D, KeyEvent.VK_NUMPAD6 -> movement(rightX(), rightY());
            //Backwards
            case KeyEvent.VK_S, KeyEvent.VK_NUMPAD2 -> movement(-forwardX(), -forwardY());
            //Turn Left
            case KeyEvent.VK_Q, KeyEvent.VK_NUMPAD7 -> turnLeft();
            //Turn Right
            case KeyEvent.VK_E, KeyEvent.VK_NUMPAD9 -> turnRight();
            //Intract
            case KeyEvent.VK_F, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> interact();
        }
        repaint();
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }
}