package org.main.engine;

import org.main.content.CharacterModelDefinition;
import org.main.core.Library;
import org.main.core.CraftingStationType;
import org.main.monsters.Monster;
import org.main.core.InventorySystem;
import org.main.core.ShopSystem;

import java.awt.image.BufferedImage;
import java.util.List;

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
    private String contentId = "";
    private List<String> questIds = List.of();
    private boolean blocksMovementOverride = false;
    private boolean renderOnWall = false;
    private double visualScale = 1.0;
    private String staticModelPath = "";
    private boolean staticModelVisible = true;
    private double staticModelOffsetX;
    private double staticModelOffsetY;
    private double staticModelOffsetZ;
    private double staticModelYawDegrees;
    private double staticModelPitchDegrees;
    private double staticModelRollDegrees;
    private double staticModelScaleMultiplier = 1.0;
    private double staticModelBrightness = 1.0;
    private CharacterModelDefinition characterModel = CharacterModelDefinition.empty();
    private String enemySpawnId = "";
    private String enemyLocalSpawnId = "";
    private String roamingAreaId = "";
    private int spawnX;
    private int spawnY;
    private int awarenessRadius = 4;
    private int movementIntervalMs = 3000;
    private int respawnDelayMs = 300000;
    private int worldAiCooldownMs;
    private boolean worldAlerted;
    private String temporaryStationId = "";
    private CraftingStationType temporaryStationType;
    private int temporaryStationRemainingMs;
    private boolean temporaryStationPendingExpiry;

    private int x;
    private int y;
    private double movementFromX;
    private double movementFromY;
    private long movementStartedNanos;
    private static final long WORLD_MOVE_INTERPOLATION_NANOS = 280_000_000L;

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
        this.spawnX = x;
        this.spawnY = y;
        withCharacterModel(monster.getCharacterModel());
    }

    public MapEntity(Monster monster, int x, int y, Library.EntityType type) {
        this(monster.getName(), type, x, y, monster.getImage());
        this.monster = monster;
        this.spawnX = x;
        this.spawnY = y;
        withCharacterModel(monster.getCharacterModel());
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

    public String getContentId() {
        return contentId;
    }

    public MapEntity withContentId(String contentId) {
        this.contentId = contentId == null ? "" : contentId.trim();
        return this;
    }

    public List<String> getQuestIds() {
        return questIds;
    }

    public MapEntity withQuestIds(List<String> questIds) {
        this.questIds = questIds == null
                ? List.of()
                : questIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
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

    public String getStaticModelPath() {
        return staticModelPath;
    }

    public boolean hasVisibleStaticModel() {
        return staticModelVisible && !staticModelPath.isBlank();
    }

    public MapEntity withStaticModel(String assetPath) {
        staticModelPath = assetPath == null ? "" : assetPath.trim().replace('\\', '/');
        staticModelVisible = !staticModelPath.isBlank();
        return this;
    }

    public MapEntity withStaticModelTransform(
            double offsetX,
            double offsetY,
            double offsetZ,
            double yawDegrees,
            double pitchDegrees,
            double rollDegrees,
            double scaleMultiplier
    ) {
        staticModelOffsetX = clampFinite(offsetX, -4.0, 4.0, 0.0);
        staticModelOffsetY = clampFinite(offsetY, -8.0, 8.0, 0.0);
        staticModelOffsetZ = clampFinite(offsetZ, -4.0, 4.0, 0.0);
        staticModelYawDegrees = normalizeDegrees(yawDegrees);
        staticModelPitchDegrees = normalizeDegrees(pitchDegrees);
        staticModelRollDegrees = normalizeDegrees(rollDegrees);
        staticModelScaleMultiplier = clampFinite(scaleMultiplier, 0.05, 20.0, 1.0);
        return this;
    }

    public double getStaticModelOffsetX() {
        return staticModelOffsetX;
    }

    public double getStaticModelOffsetY() {
        return staticModelOffsetY;
    }

    public double getStaticModelOffsetZ() {
        return staticModelOffsetZ;
    }

    public double getStaticModelYawDegrees() {
        return staticModelYawDegrees;
    }

    public double getStaticModelPitchDegrees() {
        return staticModelPitchDegrees;
    }

    public double getStaticModelRollDegrees() {
        return staticModelRollDegrees;
    }

    public double getStaticModelScaleMultiplier() {
        return staticModelScaleMultiplier;
    }

    public double getStaticModelBrightness() {
        return staticModelBrightness;
    }

    public MapEntity withStaticModelBrightness(double brightness) {
        staticModelBrightness = clampFinite(brightness, 0.0, 4.0, 1.0);
        return this;
    }

    public CharacterModelDefinition getCharacterModel() {
        return characterModel;
    }

    public MapEntity withCharacterModel(CharacterModelDefinition definition) {
        characterModel = definition == null ? CharacterModelDefinition.empty() : definition;
        if (characterModel.hasModel()) {
            withStaticModel(characterModel.modelPath());
            withVisualScale(characterModel.scale());
        }
        return this;
    }

    public void setStaticModelVisible(boolean visible) {
        staticModelVisible = visible;
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
        if (this.x != x || this.y != y) {
            movementFromX = getRenderX();
            movementFromY = getRenderY();
            movementStartedNanos = System.nanoTime();
        }
        this.x = x;
        this.y = y;
    }

    public double getRenderX() {
        return interpolateCoordinate(movementFromX, x);
    }

    public double getRenderY() {
        return interpolateCoordinate(movementFromY, y);
    }

    public boolean isVisuallyMoving() {
        return movementStartedNanos > 0
                && System.nanoTime() - movementStartedNanos < WORLD_MOVE_INTERPOLATION_NANOS;
    }

    private double interpolateCoordinate(double from, double to) {
        if (!isVisuallyMoving()) return to;
        double t = Math.max(0.0, Math.min(1.0,
                (System.nanoTime() - movementStartedNanos) / (double) WORLD_MOVE_INTERPOLATION_NANOS));
        double eased = t * t * (3.0 - 2.0 * t);
        return from + (to - from) * eased;
    }

    public MapEntity configureEnemySpawn(
            String spawnId,
            int spawnX,
            int spawnY,
            String areaId,
            int awarenessRadius,
            int movementIntervalMs,
            int respawnDelayMs
    ) {
        this.enemySpawnId = spawnId == null ? "" : spawnId;
        this.enemyLocalSpawnId = this.enemySpawnId;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.roamingAreaId = areaId == null ? "" : areaId;
        this.awarenessRadius = Math.max(0, awarenessRadius);
        this.movementIntervalMs = Math.max(250, movementIntervalMs);
        this.respawnDelayMs = Math.max(0, respawnDelayMs);
        this.worldAiCooldownMs = this.movementIntervalMs;
        return this;
    }

    public String getEnemySpawnId() {
        return enemySpawnId;
    }

    public void enterWorldWindow(String runtimeSpawnId, int offsetX, int offsetY) {
        if (enemyLocalSpawnId.isBlank()) {
            return;
        }
        enemySpawnId = runtimeSpawnId == null ? enemyLocalSpawnId : runtimeSpawnId;
        spawnX += offsetX;
        spawnY += offsetY;
    }

    public void leaveWorldWindow(int offsetX, int offsetY) {
        if (enemyLocalSpawnId.isBlank()) {
            return;
        }
        enemySpawnId = enemyLocalSpawnId;
        spawnX -= offsetX;
        spawnY -= offsetY;
    }

    public String getRoamingAreaId() {
        return roamingAreaId;
    }

    public int getSpawnX() {
        return spawnX;
    }

    public int getSpawnY() {
        return spawnY;
    }

    public int getAwarenessRadius() {
        return awarenessRadius;
    }

    public int getMovementIntervalMs() {
        return movementIntervalMs;
    }

    public int getRespawnDelayMs() {
        return respawnDelayMs;
    }

    public boolean advanceWorldAiCooldown(int deltaMs) {
        worldAiCooldownMs -= Math.max(0, deltaMs);
        if (worldAiCooldownMs > 0) {
            return false;
        }
        worldAiCooldownMs = movementIntervalMs;
        return true;
    }

    public int getWorldAiCooldownMs() {
        return worldAiCooldownMs;
    }

    public void setWorldAiCooldownMs(int value) {
        worldAiCooldownMs = Math.max(0, value);
    }

    public boolean isWorldAlerted() {
        return worldAlerted;
    }

    public void setWorldAlerted(boolean worldAlerted) {
        this.worldAlerted = worldAlerted;
    }

    public MapEntity configureTemporaryStation(
            String stationId,
            CraftingStationType stationType,
            int remainingMs,
            boolean pendingExpiry
    ) {
        temporaryStationId = stationId == null ? "" : stationId;
        temporaryStationType = stationType;
        temporaryStationRemainingMs = Math.max(0, remainingMs);
        temporaryStationPendingExpiry = pendingExpiry;
        return this;
    }

    public boolean isTemporaryStation() {
        return !temporaryStationId.isBlank() && temporaryStationType != null;
    }

    public String getTemporaryStationId() {
        return temporaryStationId;
    }

    public CraftingStationType getTemporaryStationType() {
        return temporaryStationType;
    }

    public int getTemporaryStationRemainingMs() {
        return temporaryStationRemainingMs;
    }

    public void setTemporaryStationRemainingMs(int remainingMs) {
        temporaryStationRemainingMs = Math.max(0, remainingMs);
    }

    public boolean advanceTemporaryStationTimer(long elapsedMs) {
        if (!isTemporaryStation() || temporaryStationRemainingMs <= 0) {
            return isTemporaryStation();
        }
        temporaryStationRemainingMs = (int) Math.max(
                0L,
                (long) temporaryStationRemainingMs - Math.max(0L, elapsedMs)
        );
        return temporaryStationRemainingMs == 0;
    }

    public boolean isTemporaryStationPendingExpiry() {
        return temporaryStationPendingExpiry;
    }

    public void setTemporaryStationPendingExpiry(boolean pendingExpiry) {
        temporaryStationPendingExpiry = pendingExpiry;
    }

    public MapEntity copy() {
        MapEntity copy = new MapEntity(name, type, x, y);
        copy.monster = monster;
        copy.idleAnimation = idleAnimation == null ? null : idleAnimation.copy();
        copy.staticImage = staticImage;
        copy.item = item == null ? null : item.copy();
        copy.interactionId = interactionId;
        copy.talkSoundPath = talkSoundPath;
        copy.shopBlueprint = shopBlueprint;
        copy.contentId = contentId;
        copy.questIds = List.copyOf(questIds);
        copy.blocksMovementOverride = blocksMovementOverride;
        copy.renderOnWall = renderOnWall;
        copy.visualScale = visualScale;
        copy.staticModelPath = staticModelPath;
        copy.staticModelVisible = staticModelVisible;
        copy.staticModelOffsetX = staticModelOffsetX;
        copy.staticModelOffsetY = staticModelOffsetY;
        copy.staticModelOffsetZ = staticModelOffsetZ;
        copy.staticModelYawDegrees = staticModelYawDegrees;
        copy.staticModelPitchDegrees = staticModelPitchDegrees;
        copy.staticModelRollDegrees = staticModelRollDegrees;
        copy.staticModelScaleMultiplier = staticModelScaleMultiplier;
        copy.characterModel = characterModel;
        copy.enemySpawnId = enemySpawnId;
        copy.enemyLocalSpawnId = enemyLocalSpawnId;
        copy.roamingAreaId = roamingAreaId;
        copy.spawnX = spawnX;
        copy.spawnY = spawnY;
        copy.awarenessRadius = awarenessRadius;
        copy.movementIntervalMs = movementIntervalMs;
        copy.respawnDelayMs = respawnDelayMs;
        copy.worldAiCooldownMs = worldAiCooldownMs;
        copy.worldAlerted = worldAlerted;
        copy.temporaryStationId = temporaryStationId;
        copy.temporaryStationType = temporaryStationType;
        copy.temporaryStationRemainingMs = temporaryStationRemainingMs;
        copy.temporaryStationPendingExpiry = temporaryStationPendingExpiry;
        return copy;
    }

    private static double clampFinite(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static double normalizeDegrees(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        double normalized = value % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }
}
