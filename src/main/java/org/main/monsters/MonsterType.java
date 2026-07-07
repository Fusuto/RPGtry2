package org.main.monsters;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public enum MonsterType {
    SLIME(
            "Slime",
            6,
            2,
            0,
            5,
            "A quivering mass of dungeon slime.",
            "src/main/java/org/main/images/monster/Nov-2015/mon/amorphous/jelly.png",
            "src/main/java/org/main/sounds/generated/enemy_attack.wav",
            "src/main/java/org/main/sounds/generated/player_hit.wav"
    ),

    GOBLIN(
            "Goblin",
            10,
            4,
            1,
            12,
            "A wiry goblin clutching a crude blade.",
            "src/main/java/org/main/images/monster/Nov-2015/mon/goblin.png",
            "src/main/java/org/main/sounds/generated/enemy_attack.wav",
            "src/main/java/org/main/sounds/generated/player_hit.wav"
    ),

    SKELETON(
            "Skeleton",
            12,
            5,
            2,
            18,
            "A rattling corpse animated by old magic.",
            "src/main/java/org/main/images/monster/Nov-2015/mon/undead/skeletons/skeleton_humanoid_small.png",
            "src/main/java/org/main/sounds/generated/enemy_attack.wav",
            "src/main/java/org/main/sounds/generated/player_hit.wav"
    );

    private final String displayName;
    private final int maxHp;
    private final int attack;
    private final int defense;
    private final int xpReward;
    private final String description;
    private final BufferedImage img;
    private final String attackSoundPath;
    private final String damageSoundPath;

    MonsterType(
            String displayName,
            int maxHp,
            int attack,
            int defense,
            int xpReward,
            String description,
            String imgLocation,
            String attackSoundPath, String damageSoundPath
    ) {
        this.displayName = displayName;
        this.maxHp = maxHp;
        this.attack = attack;
        this.defense = defense;
        this.xpReward = xpReward;
        this.description = description;
        this.img = loadImage(imgLocation);
        this.attackSoundPath = attackSoundPath;
        this.damageSoundPath = damageSoundPath;
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

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getAttack() {
        return attack;
    }

    public int getDefense() {
        return defense;
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
}
