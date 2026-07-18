package org.main.engine;

import org.main.core.Library;
import org.main.monsters.Monster;
import org.main.core.InventorySystem;
import org.main.core.ShopSystem;

import java.awt.image.BufferedImage;

public class MapEntity {
    private static final double MIN_VISUAL_SCALE = 0.10;

    private final String name;
    private final Library.EntityType type;

    private Monster monster;
    private SpriteAnimation idleAnimation;
    private BufferedImage staticImage;
    private InventorySystem.Item item;
    private String interactionId;
    private String talkSoundPath;
    private ShopSystem.ShopBlueprint shopBlueprint;
    private ShopSystem.ShopSession shopSession;
    private boolean blocksMovementOverride = false;
    private boolean renderOnWall = false;
    private double visualScale = 1.0;

    private int x;
    private int y;

    public MapEntity(String name, Library.EntityType type, int x, int y) {
        this.name = name;
        this.type = type;
        this.x = x;
        this.y = y;
    }

    public MapEntity(InventorySystem.Item item, int x, int y) {
        this(item.getName(), Library.EntityType.ITEM, x, y, item.getIcon());
        this.item = item;
    }

    public MapEntity(String name, Library.EntityType type, int x, int y, SpriteAnimation idleAnimation) {
        this(name, type, x, y);
        this.idleAnimation = idleAnimation;
    }

    public MapEntity(String name, Library.EntityType type, int x, int y, BufferedImage staticImage) {
        this(name, type, x, y);
        this.staticImage = staticImage;
    }

    public MapEntity(Monster monster, int x, int y) {
        this(monster.getName(), Library.EntityType.ENEMY, x, y, monster.getImage());
        this.monster = monster;
    }

    public MapEntity(Monster monster, int x, int y, Library.EntityType type) {
        this(monster.getName(), type, x, y, monster.getImage());
        this.monster = monster;
    }

    public BufferedImage getStaticImage() {
        return staticImage;
    }

    public void setStaticImage(BufferedImage staticImage) {
        this.staticImage = staticImage;
    }

    public void update(int deltaMs) {
        if (idleAnimation != null) {
            idleAnimation.update(deltaMs);
        }
    }

    public String getInteractionId() {
        return interactionId;
    }

    public void setInteractionId(String interactionId) {
        this.interactionId = interactionId;
    }

    public boolean hasInteractionId() {
        return interactionId != null && !interactionId.isBlank();
    }

    public MapEntity withInteractionId(String interactionId) {
        this.interactionId = interactionId;
        return this;
    }

    public String getTalkSoundPath() {
        return talkSoundPath;
    }

    public void setTalkSoundPath(String talkSoundPath) {
        this.talkSoundPath = talkSoundPath;
    }

    public MapEntity withTalkSoundPath(String talkSoundPath) {
        this.talkSoundPath = talkSoundPath;
        return this;
    }

    public ShopSystem.ShopBlueprint getShopBlueprint() {
        return shopBlueprint;
    }

    public MapEntity withShopBlueprint(ShopSystem.ShopBlueprint shopBlueprint) {
        this.shopBlueprint = shopBlueprint;
        this.shopSession = null;
        return this;
    }

    public ShopSystem.ShopSession getShopSession() {
        return shopSession;
    }

    public void setShopSession(ShopSystem.ShopSession shopSession) {
        this.shopSession = shopSession;
    }

    public MapEntity blocksMovement(boolean blocksMovement) {
        this.blocksMovementOverride = blocksMovement;
        return this;
    }

    public MapEntity renderOnWall(boolean renderOnWall) {
        this.renderOnWall = renderOnWall;
        return this;
    }

    public boolean shouldRenderOnWall() {
        return renderOnWall;
    }

    public double getVisualScale() {
        return visualScale;
    }

    public MapEntity withVisualScale(double visualScale) {
        this.visualScale = Math.max(MIN_VISUAL_SCALE, visualScale);
        return this;
    }

    public InventorySystem.Item getItem() {
        return item;
    }

    public SpriteAnimation getIdleAnimation() {
        return idleAnimation;
    }

    public Monster getMonster() {
        return monster;
    }

    public String getName() {
        return name;
    }

    public Library.EntityType getType() {
        return type;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isAt(int x, int y) {
        return this.x == x && this.y == y;
    }

    public boolean blocksMovement() {
        return blocksMovementOverride
                || type == Library.EntityType.ENEMY
                || type == Library.EntityType.ALLY
                || type == Library.EntityType.NPC
                || type == Library.EntityType.CHEST;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
