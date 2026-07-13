package org.main.monsters;

import org.main.content.SkillLibrary;
import org.main.core.PlayerStat;
import org.main.engine.AssetLoader;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class Monster {
    private final MonsterType type;
    private final String customId;
    private final String customName;
    private final EnumMap<PlayerStat, Integer> customStats;
    private final int customXpReward;
    private final String customDescription;
    private final BufferedImage customImage;
    private final String customAttackSoundPath;
    private final String customDamageSoundPath;
    private final List<SkillLibrary> customSkills;

    private int currentHp;

    public Monster(MonsterType type) {
        this.type = type;
        this.customId = "";
        this.customName = "";
        this.customStats = emptyStats();
        this.customXpReward = 0;
        this.customDescription = "";
        this.customImage = null;
        this.customAttackSoundPath = "";
        this.customDamageSoundPath = "";
        this.customSkills = List.of();
        this.currentHp = type.getMaxHp();
    }

    public Monster(
            String customId,
            String name,
            Map<PlayerStat, Integer> stats,
            int xpReward,
            String description,
            String imagePath,
            String attackSoundPath,
            String damageSoundPath,
            List<SkillLibrary> skills
    ) {
        this.type = null;
        this.customId = customId == null ? "" : customId;
        this.customName = name == null || name.isBlank() ? "Custom Enemy" : name;
        this.customStats = safeStats(stats);
        this.customXpReward = Math.max(0, xpReward);
        this.customDescription = description == null ? "" : description;
        this.customImage = imagePath == null || imagePath.isBlank() ? null : AssetLoader.loadImage(imagePath);
        this.customAttackSoundPath = attackSoundPath == null ? "" : attackSoundPath;
        this.customDamageSoundPath = damageSoundPath == null ? "" : damageSoundPath;
        this.customSkills = skills == null ? List.of() : List.copyOf(skills);
        this.currentHp = getMaxHp();
    }

    public MonsterType getType() {
        return type;
    }

    public String getCustomId() {
        return customId;
    }

    public String getName() {
        return type == null ? customName : type.getDisplayName();
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public int getMaxHp() {
        return getStat(PlayerStat.VITALITY);
    }

    public int getStat(PlayerStat stat) {
        return type == null ? customStats.getOrDefault(stat, 0) : type.getStat(stat);
    }

    public Map<PlayerStat, Integer> getStatsView() {
        return type == null ? Map.copyOf(customStats) : type.getStatsView();
    }

    public int getAttack() {
        return getStat(PlayerStat.ATTACK);
    }

    public int getDefense() {
        return getStat(PlayerStat.DEFENSE);
    }

    public int getXpReward() {
        return type == null ? customXpReward : type.getXpReward();
    }

    public String getDescription() {
        return type == null ? customDescription : type.getDescription();
    }

    public BufferedImage getImage() {
        return type == null ? customImage : type.getImg();
    }

    public String getAttackSoundPath() {
        return type == null ? customAttackSoundPath : type.getAttackSoundPath();
    }

    public String getDamageSoundPath() {
        return type == null ? customDamageSoundPath : type.getDamageSoundPath();
    }

    public int getIntelligence() {
        return getStat(PlayerStat.INTELLIGENCE);
    }

    public List<SkillLibrary> getSkills() {
        return type == null ? customSkills : type.getSkills();
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
}
