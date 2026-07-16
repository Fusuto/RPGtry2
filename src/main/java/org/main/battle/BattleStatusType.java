package org.main.battle;

import org.main.engine.AssetLoader;
import org.main.core.PlayerStat;

import java.awt.image.BufferedImage;

public enum BattleStatusType {
    STUN(
            "Stun",
            "Cannot act while stunned.",
            "assets/images/ui/01_UI_Resources/A1ICON/icon_ban.png",
            null,
            0,
            0.45
    ),

    ROTTING_GRASP(
            "Rotting",
            "Agility is reduced by rotting flesh.",
            "assets/images/ui/01_UI_Resources/A1ICON/Weakness.png",
            PlayerStat.AGILITY,
            2,
            0.75
    );

    private final String displayName;
    private final String description;
    private final BufferedImage icon;
    private final PlayerStat affectedStat;
    private final int defaultPotency;
    private final double defaultApplyChance;

    BattleStatusType(
            String displayName,
            String description,
            String iconPath,
            PlayerStat affectedStat,
            int defaultPotency,
            double defaultApplyChance
    ) {
        this.displayName = displayName;
        this.description = description;
        this.icon = AssetLoader.loadImage(iconPath);
        this.affectedStat = affectedStat;
        this.defaultPotency = Math.max(0, defaultPotency);
        this.defaultApplyChance = Math.max(0.0, Math.min(1.0, defaultApplyChance));
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public BufferedImage getIcon() {
        return icon;
    }

    public PlayerStat getAffectedStat() {
        return affectedStat;
    }

    public int getDefaultPotency() {
        return defaultPotency;
    }

    public double getDefaultApplyChance() {
        return defaultApplyChance;
    }
}
