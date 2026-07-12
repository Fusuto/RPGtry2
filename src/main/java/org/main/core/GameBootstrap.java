package org.main.core;

import org.main.content.ItemLibrary;
import org.main.content.GenericNpcLibrary;
import org.main.content.GatheringNodeLibrary;
import org.main.content.MainNpcLibrary;
import org.main.content.PlayerRegionLibrary;
import org.main.content.SkillLibrary;
import org.main.engine.AssetLoader;
import org.main.engine.MapEntity;
import org.main.engine.SpriteAnimation;
import org.main.monsters.Monster;
import org.main.monsters.MonsterType;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;

public final class GameBootstrap {
    private static final String CAMPFIRE_GIF = "assets/images/generic/Campfire2D.gif";
    private static final String FURNACE_FRAME_PATH = "assets/images/resourceObjects/furnace_%d.png";
    private static final String ANVIL_IMAGE_PATH = "assets/images/resourceObjects/anvil_1.png";
    private static final int FURNACE_FIRST_LIT_FRAME = 1;
    private static final int FURNACE_FRAME_COUNT = 3;
    private static final int FURNACE_FRAME_DURATION_MS = 180;
    private static final int FURNACE_X = 4;
    private static final int FURNACE_Y = 5;
    private static final int ANVIL_X = 10;
    private static final int ANVIL_Y = 5;
    private static final int BASE_PLAYER_MAX_HP = 20;
    private static final int PLAYER_MAX_HP_PER_VITALITY = 5;

    private GameBootstrap() {
    }

    public static PlayerCharacter createDefaultPlayerCharacter() {
        return createPlayerCharacter("Player", PlayerRegionLibrary.MIDLANDS);
    }

    public static PlayerCharacter createPlayerCharacter(String name, PlayerRegionLibrary playerRegion) {
        PlayerRegionLibrary selectedRegion = playerRegion == null ? PlayerRegionLibrary.MIDLANDS : playerRegion;
        EnumMap<PlayerStat, Integer> stats = PlayerCharacter.createDefaultStats();
        var battleSkills = new ArrayList<>(SkillLibrary.createUniversalPlayerSkills());

        PlayerCharacter player = new PlayerCharacter(
                name,
                startingMaxHp(stats),
                startingMaxHp(stats),
                new InventorySystem.Inventory(),
                PlayerCharacter.createDefaultSkills(),
                "assets/images/monster/Nov-2015/player/base/human_m.png",
                selectedRegion,
                stats,
                battleSkills
        );
        player.equipStarterLimbs(selectedRegion.createStarterLimbs());
        return player;
    }

    private static int startingMaxHp(EnumMap<PlayerStat, Integer> stats) {
        return BASE_PLAYER_MAX_HP + stats.get(PlayerStat.VITALITY) * PLAYER_MAX_HP_PER_VITALITY;
    }

    public static void seedTestContent(GameState gameState) {
        if (gameState == null) {
            return;
        }

        gameState.addEntityUnlessRemoved(new MapEntity(new Monster(MonsterType.SLIME), 8, 6));
        gameState.addEntityUnlessRemoved(MainNpcLibrary.TIPPING_THE_HAT_SKELETON.createEntity(8, 1));
        gameState.addEntityUnlessRemoved(GenericNpcLibrary.GOBLIN_MERCHANT.createEntity(2, 9));
        gameState.addEntityUnlessRemoved(new MapEntity(
                "Campfire",
                Library.EntityType.TRAP,
                6,
                8,
                SpriteAnimation.fromGif(CAMPFIRE_GIF, 120)
        )
                .withInteractionId("campfire_basic")
                .withVisualScale(1.65)
                .blocksMovement(true));
        if (gameState.getDungeonMap() != null) {
            gameState.getDungeonMap().setTile(FURNACE_X, FURNACE_Y, Library.TileType.FLOOR);
        }
        gameState.addEntityUnlessRemoved(new MapEntity(
                "Furnace",
                Library.EntityType.TRAP,
                FURNACE_X,
                FURNACE_Y,
                createFurnaceAnimation()
        )
                .withInteractionId("furnace_basic")
                .withVisualScale(2.65)
                .blocksMovement(true));
        if (gameState.getDungeonMap() != null) {
            gameState.getDungeonMap().setTile(ANVIL_X, ANVIL_Y, Library.TileType.FLOOR);
        }
        gameState.addEntityUnlessRemoved(new MapEntity(
                "Anvil",
                Library.EntityType.TRAP,
                ANVIL_X,
                ANVIL_Y,
                AssetLoader.loadImage(ANVIL_IMAGE_PATH)
        )
                .withInteractionId("anvil_basic")
                .withVisualScale(1.35)
                .blocksMovement(true));
        gameState.addEntityUnlessRemoved(GatheringNodeLibrary.MINERAL_ROCK_A.createEntity(8, 4));
        gameState.setTileInteractionId(10, 9, "generated_dungeon_gate");
        gameState.setTileInteractionId(4, 3, GatheringNodeLibrary.FISHING_SHOAL.getInteractionId());

        gameState.addEntityUnlessRemoved(new MapEntity(ItemLibrary.POTION.createItem(), 2, 1));

        gameState.getInventory().addItem(ItemLibrary.IRON_SWORD.createItem());
        gameState.getInventory().addItem(ItemLibrary.SILVER_RING.createItem());
    }

    private static SpriteAnimation createFurnaceAnimation() {
        BufferedImage[] frames = new BufferedImage[FURNACE_FRAME_COUNT];

        for (int i = 0; i < frames.length; i++) {
            frames[i] = AssetLoader.loadImage(String.format(FURNACE_FRAME_PATH, i + FURNACE_FIRST_LIT_FRAME));
        }

        return new SpriteAnimation(frames, FURNACE_FRAME_DURATION_MS);
    }
}
