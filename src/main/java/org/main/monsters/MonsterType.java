package org.main.monsters;

import org.main.content.SkillLibrary;
import org.main.core.PlayerStat;
import org.main.engine.AssetLoader;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public enum MonsterType {
    SLIME(
            "Slime",
            20,
            5,
            5,
            0,
            5,
            5,
            3,
            10,
            "A quivering mass of dungeon slime.",
            "assets/images/monster/Nov-2015/mon/amorphous/jelly.png",
            "assets/sounds/generated/enemy_attack.wav",
            "assets/sounds/generated/player_hit.wav",
            List.of(SkillLibrary.ABSORB)
    ),

    GOBLIN(
            "Goblin",
            10,
            4,
            4,
            1,
            4,
            4,
            2,
            12,
            "A wiry goblin clutching a crude blade.",
            "assets/images/monster/Nov-2015/mon/goblin.png",
            "assets/sounds/generated/enemy_attack.wav",
            "assets/sounds/generated/player_hit.wav",
            List.of()
    ),

    SKELETON(
            "Skeleton",
            12,
            5,
            5,
            2,
            5,
            2,
            3,
            18,
            "A rattling corpse animated by old magic.",
            "assets/images/monster/Nov-2015/mon/undead/skeletons/skeleton_humanoid_small.png",
            "assets/sounds/generated/enemy_attack.wav",
            "assets/sounds/generated/player_hit.wav",
            List.of()
    );

    private final String displayName;
    private final EnumMap<PlayerStat, Integer> stats;
    private final int xpReward;
    private final String description;
    private final BufferedImage img;
    private final String attackSoundPath;
    private final String damageSoundPath;
    private final List<SkillLibrary> skills;

    MonsterType(
            String displayName,
            int vitality,
            int attack,
            int strength,
            int defense,
            int agility,
            int intelligence,
            int willpower,
            int xpReward,
            String description,
            String imgLocation,
            String attackSoundPath,
            String damageSoundPath,
            List<SkillLibrary> skills
    ) {
        this.displayName = displayName;
        this.stats = new EnumMap<>(PlayerStat.class);
        for (PlayerStat stat : PlayerStat.values()) {
            this.stats.put(stat, 0);
        }
        this.stats.put(PlayerStat.VITALITY, Math.max(1, vitality));
        this.stats.put(PlayerStat.ATTACK, Math.max(0, attack));
        this.stats.put(PlayerStat.STRENGTH, Math.max(0, strength));
        this.stats.put(PlayerStat.DEFENSE, Math.max(0, defense));
        this.stats.put(PlayerStat.AGILITY, Math.max(0, agility));
        this.stats.put(PlayerStat.INTELLIGENCE, Math.max(0, intelligence));
        this.stats.put(PlayerStat.WILLPOWER, Math.max(0, willpower));
        this.xpReward = xpReward;
        this.description = description;
        this.img = loadImage(imgLocation);
        this.attackSoundPath = attackSoundPath;
        this.damageSoundPath = damageSoundPath;
        this.skills = List.copyOf(skills);
    }

    private BufferedImage loadImage(String path) {
        return AssetLoader.loadImage(path);
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxHp() {
        return getStat(PlayerStat.VITALITY);
    }

    public int getStat(PlayerStat stat) {
        return stats.getOrDefault(stat, 0);
    }

    public Map<PlayerStat, Integer> getStatsView() {
        return Map.copyOf(stats);
    }

    public int getAttack() {
        return getStat(PlayerStat.ATTACK);
    }

    public int getDefense() {
        return getStat(PlayerStat.DEFENSE);
    }

    public int getXpReward() {
        return xpReward;
    }

    public String getDescription() {
        return description;
    }

    public BufferedImage getImg() {
        return img;
    }

    public String getAttackSoundPath() {
        return attackSoundPath;
    }

    public String getDamageSoundPath() {
        return damageSoundPath;
    }

    public int getIntelligence() {
        return getStat(PlayerStat.INTELLIGENCE);
    }

    public List<SkillLibrary> getSkills() {
        return skills;
    }
}
