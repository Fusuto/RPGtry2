package org.main.core;

import org.main.engine.AssetLoader;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public final class PaperDollRenderer {
    private final Map<String, BufferedImage> imageCache = new HashMap<>();
    private final Map<String, BufferedImage> composedCache = new HashMap<>();

    public BufferedImage render(PlayerCharacter player) {
        if (player == null) {
            return fallbackImage();
        }

        String key = visualKey(player);
        BufferedImage cached = composedCache.get(key);
        if (cached != null) {
            return cached;
        }

        BufferedImage output = new BufferedImage(
                PaperDollSliceLibrary.canvasSize(),
                PaperDollSliceLibrary.canvasSize(),
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D g = output.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        drawLimb(g, player, LimbSlot.LEGS);
        drawLimb(g, player, LimbSlot.BODY);
        drawLimb(g, player, LimbSlot.LEFT_ARM);
        drawLimb(g, player, LimbSlot.RIGHT_ARM);
        drawLimb(g, player, LimbSlot.HEAD);
        drawEquipment(g, player);

        g.dispose();
        composedCache.put(key, output);
        return output;
    }

    private void drawLimb(Graphics2D g, PlayerCharacter player, LimbSlot slot) {
        LimbItem limb = player.getEquippedLimb(slot);
        BufferedImage source = loadImage(PaperDollAssetLibrary.sourceForLimb(limb));
        if (source == null) {
            source = fallbackImage();
        }

        for (Rectangle mask : PaperDollSliceLibrary.masksFor(slot)) {
            g.drawImage(
                    source,
                    mask.x,
                    mask.y,
                    mask.x + mask.width,
                    mask.y + mask.height,
                    mask.x,
                    mask.y,
                    mask.x + mask.width,
                    mask.y + mask.height,
                    null
            );
        }
    }

    private void drawEquipment(Graphics2D g, PlayerCharacter player) {
        InventorySystem.Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        drawEquipmentSlot(g, inventory, InventorySystem.EquipmentSlot.LEGS);
        drawEquipmentSlot(g, inventory, InventorySystem.EquipmentSlot.CHEST);
        drawEquipmentSlot(g, inventory, InventorySystem.EquipmentSlot.SHIELD);
        drawEquipmentSlot(g, inventory, InventorySystem.EquipmentSlot.WEAPON);
        drawEquipmentSlot(g, inventory, InventorySystem.EquipmentSlot.HEAD);
    }

    private void drawEquipmentSlot(Graphics2D g, InventorySystem.Inventory inventory, InventorySystem.EquipmentSlot slot) {
        InventorySystem.Item item = inventory.getEquippedItem(slot);
        String path = PaperDollAssetLibrary.overlayForItem(item, slot);
        if (path == null || path.isBlank()) {
            return;
        }

        BufferedImage overlay = loadImage(path);
        if (overlay != null) {
            g.drawImage(tintOverlay(overlay, item.getMaterial()), 0, 0, null);
        }
    }

    private BufferedImage tintOverlay(BufferedImage source, GearMaterial material) {
        Color tint = materialTint(material);
        if (source == null || tint == null) {
            return source;
        }

        BufferedImage tinted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tinted.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.setComposite(AlphaComposite.SrcAtop.derive(materialTintStrength(material)));
        g.setColor(tint);
        g.fillRect(0, 0, source.getWidth(), source.getHeight());
        g.dispose();
        return tinted;
    }

    private Color materialTint(GearMaterial material) {
        if (material == null) {
            return null;
        }

        return switch (material) {
            case COPPER -> new Color(150, 82, 44);
            case BRONZE -> new Color(176, 126, 62);
            case IRON -> new Color(165, 170, 176);
            case STEEL -> new Color(205, 210, 214);
            case SILVER -> new Color(180, 205, 220);
            case OAK -> new Color(150, 104, 56);
            case YEW -> new Color(94, 130, 72);
            case IRONWOOD -> new Color(92, 92, 82);
            case LEATHER -> new Color(120, 72, 44);
            case NONE -> null;
            default -> new Color(180, 180, 180);
        };
    }

    private float materialTintStrength(GearMaterial material) {
        return switch (material) {
            case IRON, STEEL, SILVER -> 0.35f;
            case COPPER, BRONZE, OAK, YEW, IRONWOOD, LEATHER -> 0.45f;
            case NONE -> 0.0f;
            default -> 0.35f;
        };
    }

    private BufferedImage loadImage(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        if (!imageCache.containsKey(path)) {
            imageCache.put(path, AssetLoader.loadImage(path));
        }
        return imageCache.get(path);
    }

    private BufferedImage fallbackImage() {
        BufferedImage fallback = loadImage(PaperDollAssetLibrary.DEFAULT_BASE);
        if (fallback != null) {
            return fallback;
        }
        return new BufferedImage(
                PaperDollSliceLibrary.canvasSize(),
                PaperDollSliceLibrary.canvasSize(),
                BufferedImage.TYPE_INT_ARGB
        );
    }

    private String visualKey(PlayerCharacter player) {
        StringBuilder builder = new StringBuilder();
        for (LimbSlot slot : LimbSlot.values()) {
            LimbItem limb = player.getEquippedLimb(slot);
            builder.append(slot.name()).append('=');
            if (limb != null) {
                builder.append(limb.getName())
                        .append(':')
                        .append(limb.getCondition())
                        .append(':')
                        .append(PaperDollAssetLibrary.sourceForLimb(limb));
            }
            builder.append(';');
        }

        InventorySystem.Inventory inventory = player.getInventory();
        if (inventory != null) {
            for (Map.Entry<InventorySystem.EquipmentSlot, InventorySystem.Item> entry : inventory.getEquippedItemsView().entrySet()) {
                InventorySystem.Item item = entry.getValue();
                builder.append(entry.getKey().name())
                        .append('=')
                        .append(item == null ? "" : item.getName() + ":" + item.getDurability() + ":" + item.getMaterial())
                        .append(';');
            }
        }
        return builder.toString();
    }
}
