package org.main.content;

import org.main.core.InventorySystem;
import org.main.monsters.MonsterType;

import java.util.ArrayList;
import java.util.List;

public enum MobDropsLibrary {
    SLIME_DROPS(
            MonsterType.SLIME,
            List.of(
                    DropEntry.always(ItemLibrary.SLIME),
                    new DropEntry(ItemLibrary.POTION, 0.15)
            )
    ),

    SKELETON_DROPS(
            MonsterType.SKELETON,
            List.of(
                    DropEntry.always(ItemLibrary.BONES),
                    new DropEntry(ItemLibrary.IRON_SWORD, 0.10),
                    new DropEntry(ItemLibrary.LEATHER_CAP, 0.08)
            )
    ),

    GOBLIN_DROPS(
            MonsterType.GOBLIN,
            List.of(
                    new DropEntry(ItemLibrary.IRON_SWORD, 0.12),
                    new DropEntry(ItemLibrary.POTION, 0.20)
            )
    );

    private final MonsterType monsterType;
    private final List<DropEntry> drops;

    MobDropsLibrary(MonsterType monsterType, List<DropEntry> drops) {
        this.monsterType = monsterType;
        this.drops = List.copyOf(drops);
    }

    public static List<InventorySystem.Item> rollDrops(MonsterType monsterType) {
        if (monsterType == null) {
            return List.of();
        }

        for (MobDropsLibrary table : values()) {
            if (table.monsterType == monsterType) {
                return table.rollDrops();
            }
        }

        return List.of();
    }

    private List<InventorySystem.Item> rollDrops() {
        List<InventorySystem.Item> rolledItems = new ArrayList<>();

        for (DropEntry drop : drops) {
            if (drop.rolls()) {
                rolledItems.add(drop.itemLibrary().createItem());
            }
        }

        return rolledItems;
    }

    public record DropEntry(ItemLibrary itemLibrary, double chance) {
        public static DropEntry always(ItemLibrary itemLibrary) {
            return new DropEntry(itemLibrary, 1.0);
        }

        public DropEntry {
            chance = Math.max(0.0, Math.min(1.0, chance));
        }

        private boolean rolls() {
            return itemLibrary != null && Math.random() <= chance;
        }
    }
}
