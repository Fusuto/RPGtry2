package org.main.monsters;

import org.main.content.SkillLibrary;
import org.main.core.PlayerStat;
import org.main.engine.AssetLoader;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class Monster {
    private final String customId;
    private final String customName;
    private final EnumMap<PlayerStat, Integer> customStats;
    private final int customXpReward;
    private final String customDescription;
    private final BufferedImage customImage;
    private final String customAttackSoundPath;
    private final String customDamageSoundPath;
    private final int customCombatAiIntelligence;
    private final List<SkillLibrary> customSkills;
    private final String customPaperDollSourcePath;
    private final List<DropEntry> customDrops;

    private int currentHp;

    public Monster(
            String customId,
            String name,
            Map<PlayerStat, Integer> stats,
            int xpReward,
            String description,
            String imagePath,
            String paperDollSourcePath,
            String attackSoundPath,
            String damageSoundPath,
            int combatAiIntelligence,
            List<SkillLibrary> skills,
            List<DropEntry> drops
    ) {
        this.customId = customId == null ? "" : customId;
        this.customName = name == null || name.isBlank() ? "Custom Enemy" : name;
        this.customStats = safeStats(stats);
        this.customXpReward = Math.max(0, xpReward);
        this.customDescription = description == null ? "" : description;
        this.customImage = imagePath == null || imagePath.isBlank() ? null : AssetLoader.loadImage(imagePath);
        this.customPaperDollSourcePath = paperDollSourcePath == null ? "" : paperDollSourcePath;
        this.customAttackSoundPath = attackSoundPath == null ? "" : attackSoundPath;
        this.customDamageSoundPath = damageSoundPath == null ? "" : damageSoundPath;
        this.customCombatAiIntelligence = Math.max(0, combatAiIntelligence);
        this.customSkills = skills == null ? List.of() : List.copyOf(skills);
        this.customDrops = drops == null ? List.of() : List.copyOf(drops);
        this.currentHp = getMaxHp();
    }

    public String getCustomId() {
        return customId;
    }

    public String getName() {
        return customName;
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public int getMaxHp() {
        return getStat(PlayerStat.VITALITY);
    }

    public int getStat(PlayerStat stat) {
        return customStats.getOrDefault(stat, 0);
    }

    public Map<PlayerStat, Integer> getStatsView() {
        return Map.copyOf(customStats);
    }

    public int getAttack() {
        return getStat(PlayerStat.ATTACK);
    }

    public int getDefense() {
        return getStat(PlayerStat.DEFENSE);
    }

    public int getXpReward() {
        return customXpReward;
    }

    public String getDescription() {
        return customDescription;
    }

    public BufferedImage getImage() {
        return customImage;
    }

    public String getAttackSoundPath() {
        return customAttackSoundPath;
    }

    public String getDamageSoundPath() {
        return customDamageSoundPath;
    }

    public int getIntelligence() {
        return getStat(PlayerStat.INTELLIGENCE);
    }

    public List<SkillLibrary> getSkills() {
        return customSkills;
    }

    public int getCombatAiIntelligence() {
        return customCombatAiIntelligence;
    }

    public String getPaperDollSourcePath() {
        return customPaperDollSourcePath;
    }

    public List<DropEntry> getCustomDrops() {
        return customDrops;
    }

    public boolean isAlive() {
        return currentHp > 0;
    }

    public void takeDamage(int amount) {
        currentHp = Math.max(0, currentHp - amount);
    }

    public void heal(int amount) {
        currentHp = Math.min(getMaxHp(), currentHp + amount);
    }

    private static EnumMap<PlayerStat, Integer> emptyStats() {
        EnumMap<PlayerStat, Integer> stats = new EnumMap<>(PlayerStat.class);
        for (PlayerStat stat : PlayerStat.values()) {
            stats.put(stat, 0);
        }
        stats.put(PlayerStat.VITALITY, 1);
        return stats;
    }

    private static EnumMap<PlayerStat, Integer> safeStats(Map<PlayerStat, Integer> source) {
        EnumMap<PlayerStat, Integer> stats = emptyStats();
        if (source != null) {
            for (PlayerStat stat : PlayerStat.values()) {
                int value = Math.max(0, source.getOrDefault(stat, stats.getOrDefault(stat, 0)));
                stats.put(stat, stat == PlayerStat.VITALITY ? Math.max(1, value) : value);
            }
        }
        return stats;
    }

    public record DropEntry(String itemId, double chance) {
        public DropEntry {
            itemId = itemId == null ? "" : itemId;
            chance = Math.max(0.0, Math.min(1.0, chance));
        }

        public boolean rolls() {
            return !itemId.isBlank() && Math.random() <= chance;
        }
    }
}
