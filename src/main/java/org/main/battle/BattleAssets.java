package org.main.battle;

import org.main.engine.AssetLoader;

import java.awt.image.BufferedImage;

public class BattleAssets {
    private BufferedImage battleBackground;

    private BufferedImage commandPanelBackground;
    private BufferedImage statusPanelBackground;

    private BufferedImage buttonNormal;
    private BufferedImage buttonHover;

    public static BattleAssets loadDefault() {
        BattleAssets assets = new BattleAssets();

        assets.battleBackground = loadImage("assets/images/battle_backgrounds/Willibab's Pixel Battle Backgrounds/320x240/Dungeon/Ruins_1.png");

        assets.commandPanelBackground = loadImage("assets/images/ui/01_UI_Resources/04MainMenu/menu_character_bg.png");
        assets.statusPanelBackground = loadImage("assets/images/ui/01_UI_Resources/04MainMenu/menu_character_bg.png");

        assets.buttonNormal = loadImage("assets/images/ui/01_UI_Resources/03CommonPopup + Button/common_btn.png");
        assets.buttonHover = loadImage("assets/images/ui/01_UI_Resources/03CommonPopup + Button/common_btn_choice.png");

        return assets;
    }

    private static BufferedImage loadImage(String path) {
        return AssetLoader.loadImage(path);
    }

    public BufferedImage getBattleBackground() {
        return battleBackground;
    }

    public BufferedImage getCommandPanelBackground() {
        return commandPanelBackground;
    }

    public BufferedImage getStatusPanelBackground() {
        return statusPanelBackground;
    }

    public BufferedImage getButtonNormal() {
        return buttonNormal;
    }

    public BufferedImage getButtonHover() {
        return buttonHover;
    }
}
