package org.main.core;

import org.main.battle.BattleSkill;
import org.main.engine.AssetLoader;
import org.main.monsters.MonsterType;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class LimbItem extends InventorySystem.Item {
    private final MonsterType monsterType;
    private final LimbSlot limbSlot;
    private final EnumMap<PlayerStat, Integer> baseStats;
    private final List<BattleSkill> skills;
    private final GearDurability condition;
    private final String iconPath;

    public LimbItem(
            String name,
            MonsterType monsterType,
            LimbSlot limbSlot,
            Map<PlayerStat, Integer> baseStats,
            List<BattleSkill> skills,
            GearDurability condition,
            String iconPath
    ) {
        super(name, InventorySystem.ItemType.LIMB, loadIcon(iconPath), null, 0, GearMaterial.NONE, condition, 25);
        this.monsterType = monsterType;
        this.limbSlot = limbSlot;
        this.baseStats = new EnumMap<>(PlayerStat.class);
        this.skills = skills == null ? List.of() : List.copyOf(skills);
        this.condition = condition == null ? GearDurability.GOOD : condition;
        this.iconPath = iconPath;

        for (PlayerStat stat : PlayerStat.values()) {
            this.baseStats.put(stat, Math.max(0, baseStats == null ? 0 : baseStats.getOrDefault(stat, 0)));
        }
    }

    private static BufferedImage loadIcon(String iconPath) {
        return iconPath == null || iconPath.isBlank() ? null : AssetLoader.loadImage(iconPath);
    }

    public MonsterType getMonsterType() {
        return monsterType;
    }

    public LimbSlot getLimbSlot() {
        return limbSlot;
    }

    public Map<PlayerStat, Integer> getBaseStatsView() {
        return Map.copyOf(baseStats);
    }

    public int getEffectiveStat(PlayerStat stat) {
        return (int) Math.round(baseStats.getOrDefault(stat, 0) * condition.getStatMultiplier());
    }

    public List<BattleSkill> getSkills() {
        return skills;
    }

    public GearDurability getCondition() {
        return condition;
    }

    public boolean isBroken() {
        return condition == GearDurability.BROKEN;
    }

    public LimbItem withCondition(GearDurability newCondition) {
        return new LimbItem(
                getName(),
                monsterType,
                limbSlot,
                baseStats,
                skills,
                newCondition,
                iconPath
        );
    }

    @Override
    public LimbItem copy() {
        return withCondition(condition);
    }
}
