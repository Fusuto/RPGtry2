package org.main.battle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class BattleAssets {
    private BufferedImage battleBackground;

    private BufferedImage commandPanelBackground;
    private BufferedImage statusPanelBackground;

    private BufferedImage buttonNormal;
    private BufferedImage buttonHover;

    public static BattleAssets loadDefault() {
        BattleAssets assets = new BattleAssets();

        assets.battleBackground = loadImage("src/main/java/org/main/images/battle_backgrounds/Willibab's Pixel Battle Backgrounds/320x240/Dungeon/Ruins_1.png");

        assets.commandPanelBackground = loadImage("src/main/java/org/main/images/ui/01_UI_Resources/04MainMenu/menu_character_bg.png");
        assets.statusPanelBackground = loadImage("src/main/java/org/main/images/ui/01_UI_Resources/04MainMenu/menu_character_bg.png");

        assets.buttonNormal = loadImage("src/main/java/org/main/images/ui/01_UI_Resources/03CommonPopup + Button/common_btn.png");
        assets.buttonHover = loadImage("src/main/java/org/main/images/ui/01_UI_Resources/03CommonPopup + Button/common_btn_choice.png");

        return assets;
    }

    private static BufferedImage loadImage(String path) {
        try {
            File file = new File(path);

            if (!file.exists()) {
                System.out.println("Battle asset not found: " + path);
                return null;
            }

            return ImageIO.read(file);
        } catch (IOException e) {
            System.out.println("Failed to load battle asset: " + path);
            e.printStackTrace();
            return null;
        }
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