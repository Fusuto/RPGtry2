package org.main.battle;

import org.main.engine.AssetLoader;

import java.awt.image.BufferedImage;

public enum BattleStatusType {
    STUN(
            "Stun",
            "Cannot act while stunned.",
            "assets/images/ui/01_UI_Resources/A1ICON/icon_ban.png"
    );

    private final String displayName;
    private final String description;
    private final BufferedImage icon;

    BattleStatusType(String displayName, String description, String iconPath) {
        this.displayName = displayName;
        this.description = description;
        this.icon = AssetLoader.loadImage(iconPath);
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
}
