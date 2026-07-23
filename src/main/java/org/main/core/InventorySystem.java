package org.main.core;

import org.main.engine.AssetLoader;
import org.main.engine.MapEntity;
import org.main.engine.SoundSystem;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;

public final class InventorySystem {
    private static final double SELL_PRICE_MULTIPLIER = 0.35;

    private InventorySystem() {
    }

    public enum ItemType {
        HEAD_GEAR,
        CHEST_ARMOR,
        LEG_ARMOR,
        RING,
        WEAPON,
        SHIELD,
        LIMB,
        MISC,
        CONSUMABLE
    }

    public enum EquipmentSlot {
        HEAD("Head"),
        CHEST("Chest"),
        LEGS("Legs"),
        RING_LEFT("Ring"),
        RING_RIGHT("Ring"),
        WEAPON("Weapon"),
        SHIELD("Shield");

        private final String label;

        EquipmentSlot(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public static class Item {
        private final String name;
        private final ItemType itemType;
        private final BufferedImage icon;
        private final String useSoundPath;
        private final int healAmount;
        private final GearMaterial material;
        private final GearDurability durability;
        private final int baseGoldValue;
        private final String examineText;
        private final PlayerStat statBonusTarget;
        private final String paperDollOverlayPath;
        private final WeaponType weaponType;
        private final boolean twoHanded;
        private final boolean stackable;
        private final boolean materialTintApplied;
        private int magicAccuracyBonus;
        private int magicPowerBonus;
        private String firstPersonModelPath = "";
        private EquipmentViewModelProfile viewModelProfile = EquipmentViewModelProfile.defaults();
        private int quantity;

        public Item(String name, ItemType itemType, BufferedImage icon) {
            this(name, itemType, icon, null, 0);
        }

        public Item(String name, ItemType itemType, BufferedImage icon, String useSoundPath) {
            this(name, itemType, icon, useSoundPath, 0);
        }

        public Item(String name, ItemType itemType, BufferedImage icon, String useSoundPath, int healAmount) {
            this(name, itemType, icon, useSoundPath, healAmount, GearMaterial.NONE, GearDurability.PERFECT, 10, "");
        }

        public Item(
                String name,
                ItemType itemType,
                BufferedImage icon,
                String useSoundPath,
                int healAmount,
                GearMaterial material,
                GearDurability durability,
                int baseGoldValue
        ) {
            this(name, itemType, icon, useSoundPath, healAmount, material, durability, baseGoldValue, "");
        }

        public Item(
                String name,
                ItemType itemType,
                BufferedImage icon,
                String useSoundPath,
                int healAmount,
                GearMaterial material,
                GearDurability durability,
                int baseGoldValue,
                String examineText
        ) {
            this(name, itemType, icon, useSoundPath, healAmount, material, durability, baseGoldValue, examineText, null);
        }

        public Item(
                String name,
                ItemType itemType,
                BufferedImage icon,
                String useSoundPath,
                int healAmount,
                GearMaterial material,
                GearDurability durability,
                int baseGoldValue,
                String examineText,
                PlayerStat statBonusTarget
        ) {
            this(name, itemType, icon, useSoundPath, healAmount, material, durability, baseGoldValue, examineText, statBonusTarget, "");
        }

        public Item(
                String name,
                ItemType itemType,
                BufferedImage icon,
                String useSoundPath,
                int healAmount,
                GearMaterial material,
                GearDurability durability,
                int baseGoldValue,
                String examineText,
                PlayerStat statBonusTarget,
                String paperDollOverlayPath
        ) {
            this(name, itemType, icon, useSoundPath, healAmount, material, durability, baseGoldValue, examineText, statBonusTarget, paperDollOverlayPath, defaultWeaponType(itemType));
        }

        public Item(
                String name,
                ItemType itemType,
                BufferedImage icon,
                String useSoundPath,
                int healAmount,
                GearMaterial material,
                GearDurability durability,
                int baseGoldValue,
                String examineText,
                PlayerStat statBonusTarget,
                String paperDollOverlayPath,
                WeaponType weaponType
        ) {
            this(name, itemType, icon, useSoundPath, healAmount, material, durability, baseGoldValue, examineText, statBonusTarget, false, 1, false, paperDollOverlayPath, weaponType, false);
        }

        public Item(
                String name,
                ItemType itemType,
                BufferedImage icon,
                String useSoundPath,
                int healAmount,
                GearMaterial material,
                GearDurability durability,
                int baseGoldValue,
                String examineText,
                PlayerStat statBonusTarget,
                boolean stackable,
                int quantity,
                String paperDollOverlayPath,
                WeaponType weaponType
        ) {
            this(name, itemType, icon, useSoundPath, healAmount, material, durability, baseGoldValue, examineText, statBonusTarget, stackable, quantity, false, paperDollOverlayPath, weaponType, false);
        }

        public Item(
                String name,
                ItemType itemType,
                BufferedImage icon,
                String useSoundPath,
                int healAmount,
                GearMaterial material,
                GearDurability durability,
                int baseGoldValue,
                String examineText,
                PlayerStat statBonusTarget,
                boolean stackable,
                int quantity,
                String paperDollOverlayPath,
                WeaponType weaponType,
                boolean twoHanded
        ) {
            this(name, itemType, icon, useSoundPath, healAmount, material, durability, baseGoldValue, examineText, statBonusTarget, stackable, quantity, false, paperDollOverlayPath, weaponType, twoHanded);
        }

        public Item(
                String name,
                ItemType itemType,
                BufferedImage icon,
                String useSoundPath,
                int healAmount,
                GearMaterial material,
                GearDurability durability,
                int baseGoldValue,
                String examineText,
                PlayerStat statBonusTarget,
                boolean stackable,
                int quantity
        ) {
            this(
                    name,
                    itemType,
                    icon,
                    useSoundPath,
                    healAmount,
                    material,
                    durability,
                    baseGoldValue,
                    examineText,
                    statBonusTarget,
                    stackable,
                    quantity,
                    false,
                    "",
                    defaultWeaponType(itemType),
                    false
            );
        }

        private Item(
                String name,
                ItemType itemType,
                BufferedImage icon,
                String useSoundPath,
                int healAmount,
                GearMaterial material,
                GearDurability durability,
                int baseGoldValue,
                String examineText,
                PlayerStat statBonusTarget,
                boolean stackable,
                int quantity,
                boolean materialTintApplied,
                String paperDollOverlayPath,
                WeaponType weaponType,
                boolean twoHanded
        ) {
            this.name = name;
            this.itemType = itemType;
            this.useSoundPath = useSoundPath;
            this.healAmount = Math.max(0, healAmount);
            this.material = material == null ? GearMaterial.NONE : material;
            this.durability = durability == null ? GearDurability.PERFECT : durability;
            this.materialTintApplied = materialTintApplied || shouldApplyMaterialTint(icon, itemType, this.material);
            this.icon = materialTintApplied ? icon : applyMaterialTint(icon, itemType, this.material);
            this.baseGoldValue = Math.max(1, baseGoldValue);
            this.examineText = examineText == null || examineText.isBlank()
                    ? "There is nothing unusual about it."
                    : examineText;
            this.statBonusTarget = statBonusTarget;
            this.paperDollOverlayPath = paperDollOverlayPath == null ? "" : paperDollOverlayPath.trim();
            this.weaponType = itemType == ItemType.WEAPON
                    ? (weaponType == null || weaponType == WeaponType.NONE ? WeaponType.SWORD : weaponType)
                    : WeaponType.NONE;
            this.twoHanded = itemType == ItemType.WEAPON && twoHanded;
            this.stackable = stackable;
            this.quantity = Math.max(1, quantity);
        }

        public Item withMagicBonuses(int accuracyBonus, int powerBonus) {
            this.magicAccuracyBonus = Math.max(0, accuracyBonus);
            this.magicPowerBonus = Math.max(0, powerBonus);
            return this;
        }

        public Item withFirstPersonModel(String modelPath) {
            String normalized = modelPath == null ? "" : modelPath.trim().replace('\\', '/');
            firstPersonModelPath = normalized;
            return this;
        }

        public Item withViewModelProfile(EquipmentViewModelProfile profile) {
            viewModelProfile = profile == null ? EquipmentViewModelProfile.defaults() : profile;
            return this;
        }

        private static WeaponType defaultWeaponType(ItemType itemType) {
            return itemType == ItemType.WEAPON ? WeaponType.SWORD : WeaponType.NONE;
        }

        public Item(String name, ItemType itemType, String iconPath) {
            this(name, itemType, iconPath, null, 0);
        }

        public Item(String name, ItemType itemType, String iconPath, String useSoundPath) {
            this(name, itemType, iconPath, useSoundPath, 0);
        }

        public Item(String name, ItemType itemType, String iconPath, String useSoundPath, int healAmount) {
            this(name, itemType, loadIcon(iconPath), useSoundPath, healAmount);
        }

        public Item(
                String name,
                ItemType itemType,
                String iconPath,
                String useSoundPath,
                int healAmount,
                GearMaterial material,
                GearDurability durability,
                int baseGoldValue
        ) {
            this(name, itemType, loadIcon(iconPath), useSoundPath, healAmount, material, durability, baseGoldValue);
        }

        public Item(
                String name,
                ItemType itemType,
                String iconPath,
                String useSoundPath,
                int healAmount,
                GearMaterial material,
                GearDurability durability,
                int baseGoldValue,
                String examineText
        ) {
            this(name, itemType, loadIcon(iconPath), useSoundPath, healAmount, material, durability, baseGoldValue, examineText);
        }

        public Item(
                String name,
                ItemType itemType,
                String iconPath,
                String useSoundPath,
                int healAmount,
                GearMaterial material,
                GearDurability durability,
                int baseGoldValue,
                String examineText,
                PlayerStat statBonusTarget
        ) {
            this(name, itemType, loadIcon(iconPath), useSoundPath, healAmount, material, durability, baseGoldValue, examineText, statBonusTarget);
        }

        public Item(
                String name,
                ItemType itemType,
                String iconPath,
                String useSoundPath,
                int healAmount,
                GearMaterial material,
                GearDurability durability,
                int baseGoldValue,
                String examineText,
                PlayerStat statBonusTarget,
                String paperDollOverlayPath
        ) {
            this(name, itemType, loadIcon(iconPath), useSoundPath, healAmount, material, durability, baseGoldValue, examineText, statBonusTarget, paperDollOverlayPath);
        }

        public Item(
                String name,
                ItemType itemType,
                String iconPath,
                String useSoundPath,
                int healAmount,
                GearMaterial material,
                GearDurability durability,
                int baseGoldValue,
                String examineText,
                PlayerStat statBonusTarget,
                String paperDollOverlayPath,
                WeaponType weaponType
        ) {
            this(name, itemType, loadIcon(iconPath), useSoundPath, healAmount, material, durability, baseGoldValue, examineText, statBonusTarget, paperDollOverlayPath, weaponType);
        }

        public Item(
                String name,
                ItemType itemType,
                String iconPath,
                String useSoundPath,
                int healAmount,
                GearMaterial material,
                GearDurability durability,
                int baseGoldValue,
                String examineText,
                PlayerStat statBonusTarget,
                boolean stackable,
                int quantity,
                String paperDollOverlayPath,
                WeaponType weaponType
        ) {
            this(name, itemType, loadIcon(iconPath), useSoundPath, healAmount, material, durability, baseGoldValue, examineText, statBonusTarget, stackable, quantity, false, paperDollOverlayPath, weaponType, false);
        }

        public Item(
                String name,
                ItemType itemType,
                String iconPath,
                String useSoundPath,
                int healAmount,
                GearMaterial material,
                GearDurability durability,
                int baseGoldValue,
                String examineText,
                PlayerStat statBonusTarget,
                boolean stackable,
                int quantity,
                String paperDollOverlayPath,
                WeaponType weaponType,
                boolean twoHanded
        ) {
            this(name, itemType, loadIcon(iconPath), useSoundPath, healAmount, material, durability, baseGoldValue, examineText, statBonusTarget, stackable, quantity, false, paperDollOverlayPath, weaponType, twoHanded);
        }

        private static BufferedImage loadIcon(String iconPath) {
            if (iconPath == null || iconPath.isBlank()) {
                return null;
            }

            return AssetLoader.loadImage(iconPath);
        }

        private static BufferedImage applyMaterialTint(BufferedImage source, ItemType itemType, GearMaterial material) {
            if (!shouldApplyMaterialTint(source, itemType, material)) {
                return source;
            }

            Color tint = materialTint(material);

            BufferedImage tinted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = tinted.createGraphics();
            graphics.drawImage(source, 0, 0, null);
            graphics.setComposite(AlphaComposite.SrcAtop.derive(materialTintStrength(material)));
            graphics.setColor(tint);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.dispose();
            return tinted;
        }

        public static BufferedImage applyBurntTint(BufferedImage source) {
            if (source == null) {
                return null;
            }

            BufferedImage tinted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = tinted.createGraphics();
            graphics.drawImage(source, 0, 0, null);
            graphics.setComposite(AlphaComposite.SrcAtop.derive(0.78f));
            graphics.setColor(new Color(12, 10, 8));
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.dispose();
            return tinted;
        }

        private static boolean shouldApplyMaterialTint(BufferedImage source, ItemType itemType, GearMaterial material) {
            return source != null
                    && itemType != null
                    && material != null
                    && material != GearMaterial.NONE
                    && isTintableEquipmentType(itemType)
                    && materialTint(material) != null;
        }

        private static boolean isTintableEquipmentType(ItemType itemType) {
            return itemType == ItemType.WEAPON
                    || itemType == ItemType.SHIELD
                    || itemType == ItemType.HEAD_GEAR
                    || itemType == ItemType.CHEST_ARMOR
                    || itemType == ItemType.LEG_ARMOR;
        }

        private static Color materialTint(GearMaterial material) {
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

        private static float materialTintStrength(GearMaterial material) {
            return switch (material) {
                case IRON, STEEL, SILVER -> 0.35f;
                case COPPER, BRONZE, OAK, YEW, IRONWOOD, LEATHER -> 0.45f;
                case NONE -> 0.0f;
                default -> 0.35f;
            };
        }

        public String getName() {
            return name;
        }

        public ItemType getItemType() {
            return itemType;
        }

        public BufferedImage getIcon() {
            return icon;
        }

        public String getUseSoundPath() {
            return useSoundPath;
        }

        public int getHealAmount() {
            return healAmount;
        }

        public GearMaterial getMaterial() {
            return material;
        }

        public GearDurability getDurability() {
            return durability;
        }

        public int getBaseGoldValue() {
            return baseGoldValue;
        }

        public boolean isStackable() {
            return stackable;
        }

        public int getQuantity() {
            return quantity;
        }

        public void addQuantity(int amount) {
            if (stackable && amount > 0) {
                long nextQuantity = (long) quantity + amount;
                quantity = (int) Math.min(Integer.MAX_VALUE, nextQuantity);
            }
        }

        public boolean removeQuantity(int amount) {
            if (!stackable || amount <= 0 || quantity < amount) {
                return false;
            }
            quantity -= amount;
            return true;
        }

        public String getExamineText() {
            return examineText;
        }

        public PlayerStat getStatBonusTarget() {
            return statBonusTarget;
        }

        public String getPaperDollOverlayPath() {
            return paperDollOverlayPath;
        }

        public WeaponType getWeaponType() {
            return weaponType;
        }

        public boolean isTwoHanded() {
            return twoHanded;
        }

        public int getEffectiveStatBonus() {
            if (!isEquippable()) {
                return 0;
            }

            return (int) Math.round(material.getStatBonus() * durability.getStatMultiplier());
        }

        public int getWeaponAccuracyBonus() {
            if (itemType != ItemType.WEAPON) {
                return 0;
            }
            return Math.max(0, getEffectiveStatBonus() + weaponType.getAccuracyBonus());
        }

        public int getWeaponPowerBonus() {
            if (itemType != ItemType.WEAPON) {
                return 0;
            }
            return Math.max(0, getEffectiveStatBonus() + weaponType.getPowerBonus());
        }

        public int getMagicAccuracyBonus() {
            return itemType == ItemType.WEAPON ? magicAccuracyBonus : 0;
        }

        public int getMagicPowerBonus() {
            return itemType == ItemType.WEAPON ? magicPowerBonus : 0;
        }

        public String getFirstPersonModelPath() {
            return firstPersonModelPath;
        }

        public boolean hasFirstPersonModel() {
            return !firstPersonModelPath.isBlank();
        }

        public EquipmentViewModelProfile getViewModelProfile() {
            return viewModelProfile;
        }

        public double getWeaponSpeedMultiplier() {
            return itemType == ItemType.WEAPON ? weaponType.getSpeedMultiplier() : 1.0;
        }

        public int getCalculatedBuyPrice() {
            return Math.max(1, (int) Math.round(
                    baseGoldValue
                            * material.getPriceMultiplier()
                            * durability.getPriceMultiplier()
            ));
        }

        public int getCalculatedSellPrice() {
            return Math.max(1, (int) Math.floor(getCalculatedBuyPrice() * SELL_PRICE_MULTIPLIER));
        }

        public boolean isEquippable() {
            return itemType == ItemType.HEAD_GEAR
                    || itemType == ItemType.CHEST_ARMOR
                    || itemType == ItemType.LEG_ARMOR
                    || itemType == ItemType.RING
                    || itemType == ItemType.WEAPON
                    || itemType == ItemType.SHIELD;
        }

        public Item copy() {
            return new Item(
                    name,
                    itemType,
                    icon,
                    useSoundPath,
                    healAmount,
                    material,
                    durability,
                    baseGoldValue,
                    examineText,
                    statBonusTarget,
                    stackable,
                    quantity,
                    materialTintApplied,
                    paperDollOverlayPath,
                    weaponType,
                    twoHanded
            ).withMagicBonuses(magicAccuracyBonus, magicPowerBonus)
                    .withFirstPersonModel(firstPersonModelPath)
                    .withViewModelProfile(viewModelProfile);
        }
    }

    public static class Inventory {
        public static final int GRID_COLUMNS = 5;
        public static final int GRID_ROWS = 5;
        public static final int SLOT_COUNT = GRID_COLUMNS * GRID_ROWS;

        private final Item[] items = new Item[SLOT_COUNT];
        private final Map<EquipmentSlot, Item> equippedItems = new EnumMap<>(EquipmentSlot.class);

        public Item getItem(int index) {
            if (!isValidInventoryIndex(index)) {
                return null;
            }

            return items[index];
        }

        public Item getEquippedItem(EquipmentSlot slot) {
            return equippedItems.get(slot);
        }

        public int getTotalEquipmentStatBonus() {
            int total = 0;

            for (Item item : equippedItems.values()) {
                if (item != null) {
                    total += item.getEffectiveStatBonus();
                }
            }

            return total;
        }

        public int getWeaponStatBonus() {
            Item weapon = equippedItems.get(EquipmentSlot.WEAPON);
            return weapon == null ? 0 : weapon.getEffectiveStatBonus();
        }

        public int getWeaponAccuracyBonus() {
            Item weapon = equippedItems.get(EquipmentSlot.WEAPON);
            return weapon == null ? 0 : weapon.getWeaponAccuracyBonus();
        }

        public int getWeaponPowerBonus() {
            Item weapon = equippedItems.get(EquipmentSlot.WEAPON);
            return weapon == null ? 0 : weapon.getWeaponPowerBonus();
        }

        public double getWeaponSpeedMultiplier() {
            Item weapon = equippedItems.get(EquipmentSlot.WEAPON);
            return weapon == null ? 1.0 : weapon.getWeaponSpeedMultiplier();
        }

        public int getArmorStatBonus() {
            int total = 0;

            for (Map.Entry<EquipmentSlot, Item> entry : equippedItems.entrySet()) {
                if (entry.getKey() != EquipmentSlot.WEAPON && entry.getValue() != null) {
                    total += entry.getValue().getEffectiveStatBonus();
                }
            }

            return total;
        }

        public boolean addItem(Item item) {
            if (item == null) {
                return false;
            }

            if (item.isStackable()) {
                Item stack = findStack(item.getName());
                if (stack != null) {
                    stack.addQuantity(item.getQuantity());
                    return true;
                }
            }

            for (int i = 0; i < items.length; i++) {
                if (items[i] == null) {
                    items[i] = item;
                    return true;
                }
            }

            return false;
        }

        public Item removeItem(int index) {
            if (!isValidInventoryIndex(index)) {
                return null;
            }

            Item removedItem = items[index];
            items[index] = null;

            return removedItem;
        }

        public boolean removeFirstItemNamed(String itemName) {
            if (itemName == null || itemName.isBlank()) {
                return false;
            }

            for (int i = 0; i < items.length; i++) {
                Item item = items[i];

                if (item != null && itemName.equalsIgnoreCase(item.getName())) {
                    if (item.isStackable() && item.getQuantity() > 1) {
                        item.removeQuantity(1);
                    } else {
                        items[i] = null;
                    }
                    return true;
                }
            }

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                Item item = equippedItems.get(slot);

                if (item != null && itemName.equalsIgnoreCase(item.getName())) {
                    equippedItems.remove(slot);
                    return true;
                }
            }

            return false;
        }

        public boolean canAddItem(Item item) {
            if (item == null) {
                return false;
            }
            return item.isStackable() && findStack(item.getName()) != null || hasFreeSlot();
        }

        public int countItemNamed(String itemName) {
            if (itemName == null || itemName.isBlank()) {
                return 0;
            }

            int count = 0;
            for (Item item : items) {
                if (item != null && itemName.equalsIgnoreCase(item.getName())) {
                    count += item.isStackable() ? item.getQuantity() : 1;
                }
            }

            for (Item item : equippedItems.values()) {
                if (item != null && itemName.equalsIgnoreCase(item.getName())) {
                    count += item.isStackable() ? item.getQuantity() : 1;
                }
            }

            return count;
        }

        public boolean removeItemQuantityNamed(String itemName, int amount) {
            if (itemName == null || itemName.isBlank() || amount <= 0 || countItemNamed(itemName) < amount) {
                return false;
            }

            int remaining = amount;
            for (int i = 0; i < items.length && remaining > 0; i++) {
                Item item = items[i];
                if (item == null || !itemName.equalsIgnoreCase(item.getName())) {
                    continue;
                }

                if (item.isStackable()) {
                    int removed = Math.min(remaining, item.getQuantity());
                    item.removeQuantity(removed);
                    remaining -= removed;
                    if (item.getQuantity() <= 0) {
                        items[i] = null;
                    }
                } else {
                    items[i] = null;
                    remaining--;
                }
            }

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (remaining <= 0) {
                    break;
                }

                Item item = equippedItems.get(slot);
                if (item != null && itemName.equalsIgnoreCase(item.getName())) {
                    equippedItems.remove(slot);
                    remaining--;
                }
            }

            return remaining == 0;
        }

        public boolean wouldRemovingQuantityFreeSlot(String itemName, int amount) {
            if (itemName == null || itemName.isBlank() || amount <= 0) {
                return false;
            }
            int remaining = amount;
            for (Item item : items) {
                if (item == null || !itemName.equalsIgnoreCase(item.getName())) {
                    continue;
                }
                int inSlot = item.isStackable() ? item.getQuantity() : 1;
                if (remaining >= inSlot) {
                    return true;
                }
                remaining -= Math.min(remaining, inSlot);
                if (remaining <= 0) {
                    return false;
                }
            }
            return false;
        }

        public int findFirstItemIndexNamed(String itemName) {
            if (itemName == null || itemName.isBlank()) {
                return -1;
            }

            for (int i = 0; i < items.length; i++) {
                Item item = items[i];

                if (item != null && itemName.equalsIgnoreCase(item.getName())) {
                    return i;
                }
            }

            return -1;
        }

        public boolean hasItemNamed(String itemName) {
            if (itemName == null || itemName.isBlank()) {
                return false;
            }

            for (Item item : items) {
                if (item != null && itemName.equalsIgnoreCase(item.getName())) {
                    return true;
                }
            }

            for (Item item : equippedItems.values()) {
                if (item != null && itemName.equalsIgnoreCase(item.getName())) {
                    return true;
                }
            }

            return false;
        }

        public void clear() {
            for (int i = 0; i < items.length; i++) {
                items[i] = null;
            }

            equippedItems.clear();
        }

        public Map<EquipmentSlot, Item> getEquippedItemsView() {
            return Map.copyOf(equippedItems);
        }

        public void setEquippedItem(EquipmentSlot slot, Item item) {
            if (slot == null) {
                return;
            }

            if (item == null) {
                equippedItems.remove(slot);
                return;
            }

            if (canEquipToSlot(item, slot)) {
                if (slot == EquipmentSlot.WEAPON && item.isTwoHanded()) {
                    equippedItems.remove(EquipmentSlot.SHIELD);
                }
                equippedItems.put(slot, item);
            }
        }

        public boolean hasFreeSlot() {
            for (Item item : items) {
                if (item == null) {
                    return true;
                }
            }

            return false;
        }

        public int getFirstFreeSlotIndex() {
            for (int i = 0; i < items.length; i++) {
                if (items[i] == null) {
                    return i;
                }
            }

            return -1;
        }

        public boolean addItemAt(Item item, int index) {
            if (item == null || !isValidInventoryIndex(index)) {
                return false;
            }

            if (item.isStackable()) {
                Item stack = findStack(item.getName());
                if (stack != null) {
                    stack.addQuantity(item.getQuantity());
                    return true;
                }
            }

            if (items[index] != null) {
                return false;
            }

            items[index] = item;
            return true;
        }

        public void swapInventorySlots(int firstIndex, int secondIndex) {
            if (!isValidInventoryIndex(firstIndex) || !isValidInventoryIndex(secondIndex)) {
                return;
            }

            Item temp = items[firstIndex];
            items[firstIndex] = items[secondIndex];
            items[secondIndex] = temp;
        }

        public boolean equipFromInventory(int inventoryIndex) {
            Item item = getItem(inventoryIndex);

            if (item == null) {
                return false;
            }

            EquipmentSlot slot = getPreferredEquipmentSlot(item);

            if (slot == null) {
                return false;
            }

            return equipFromInventory(inventoryIndex, slot);
        }

        public boolean equipFromInventory(int inventoryIndex, EquipmentSlot targetSlot) {
            Item item = getItem(inventoryIndex);

            if (item == null || targetSlot == null) {
                return false;
            }

            if (!canEquipToSlot(item, targetSlot)) {
                return false;
            }

            Item previouslyEquipped = equippedItems.get(targetSlot);
            Item displacedShield = targetSlot == EquipmentSlot.WEAPON && item.isTwoHanded()
                    ? equippedItems.get(EquipmentSlot.SHIELD)
                    : null;

            if (displacedShield != null && previouslyEquipped != null && !hasFreeSlot()) {
                return false;
            }

            equippedItems.put(targetSlot, item);
            if (displacedShield != null) {
                equippedItems.remove(EquipmentSlot.SHIELD);
            }
            items[inventoryIndex] = previouslyEquipped == null ? displacedShield : previouslyEquipped;
            if (previouslyEquipped != null && displacedShield != null && !addItem(displacedShield)) {
                equippedItems.put(targetSlot, previouslyEquipped);
                equippedItems.put(EquipmentSlot.SHIELD, displacedShield);
                items[inventoryIndex] = item;
                return false;
            }

            return true;
        }

        public boolean unequipToInventory(EquipmentSlot slot) {
            Item equipped = equippedItems.get(slot);

            if (equipped == null) {
                return false;
            }

            if (!addItem(equipped)) {
                return false;
            }

            equippedItems.remove(slot);
            return true;
        }

        public boolean unequipToInventory(EquipmentSlot slot, int targetInventoryIndex) {
            if (slot == null || !isValidInventoryIndex(targetInventoryIndex)) {
                return false;
            }

            Item equipped = equippedItems.get(slot);

            if (equipped == null) {
                return false;
            }

            Item inventoryItem = items[targetInventoryIndex];

            /*
             * Empty inventory slot: simply unequip into it.
             */
            if (inventoryItem == null) {
                items[targetInventoryIndex] = equipped;
                equippedItems.remove(slot);
                return true;
            }

            /*
             * Occupied inventory slot: swap only if the inventory item can be
             * equipped into the slot we are dragging from.
             */
            if (!canEquipToSlot(inventoryItem, slot)) {
                return false;
            }

            if (slot == EquipmentSlot.WEAPON && inventoryItem.isTwoHanded()
                    && equippedItems.get(EquipmentSlot.SHIELD) != null) {
                return false;
            }

            items[targetInventoryIndex] = equipped;
            equippedItems.put(slot, inventoryItem);

            return true;
        }

        public boolean moveEquippedItem(EquipmentSlot fromSlot, EquipmentSlot toSlot) {
            if (fromSlot == null || toSlot == null || fromSlot == toSlot) {
                return false;
            }

            Item movingItem = equippedItems.get(fromSlot);

            if (movingItem == null) {
                return false;
            }

            if (!canEquipToSlot(movingItem, toSlot)) {
                return false;
            }

            Item targetItem = equippedItems.get(toSlot);
            if (toSlot == EquipmentSlot.WEAPON && movingItem.isTwoHanded()
                    && equippedItems.get(EquipmentSlot.SHIELD) != null
                    && fromSlot != EquipmentSlot.SHIELD) {
                return false;
            }

            /*
             * If the target equipment slot has an item, only swap if that item
             * can legally go into the original slot.
             */
            if (targetItem != null && !canEquipToSlot(targetItem, fromSlot)) {
                return false;
            }

            equippedItems.put(toSlot, movingItem);

            if (targetItem != null) {
                equippedItems.put(fromSlot, targetItem);
            } else {
                equippedItems.remove(fromSlot);
            }

            return true;
        }

        public boolean canEquipToSlot(Item item, EquipmentSlot slot) {
            if (item == null || slot == null) {
                return false;
            }

            return switch (item.getItemType()) {
                case HEAD_GEAR -> slot == EquipmentSlot.HEAD;
                case CHEST_ARMOR -> slot == EquipmentSlot.CHEST;
                case LEG_ARMOR -> slot == EquipmentSlot.LEGS;
                case RING -> slot == EquipmentSlot.RING_LEFT || slot == EquipmentSlot.RING_RIGHT;
                case WEAPON -> slot == EquipmentSlot.WEAPON;
                case SHIELD -> slot == EquipmentSlot.SHIELD
                        && !isTwoHandedWeaponEquipped();
                case LIMB, MISC, CONSUMABLE -> false;
            };
        }

        private boolean isTwoHandedWeaponEquipped() {
            Item weapon = equippedItems.get(EquipmentSlot.WEAPON);
            return weapon != null && weapon.isTwoHanded();
        }

        public EquipmentSlot getPreferredEquipmentSlot(Item item) {
            if (item == null) {
                return null;
            }

            return switch (item.getItemType()) {
                case HEAD_GEAR -> EquipmentSlot.HEAD;
                case CHEST_ARMOR -> EquipmentSlot.CHEST;
                case LEG_ARMOR -> EquipmentSlot.LEGS;
                case WEAPON -> EquipmentSlot.WEAPON;
                case SHIELD -> EquipmentSlot.SHIELD;
                case RING -> getPreferredRingSlot();
                case LIMB, MISC, CONSUMABLE -> null;
            };
        }

        private EquipmentSlot getPreferredRingSlot() {
            if (equippedItems.get(EquipmentSlot.RING_LEFT) == null) {
                return EquipmentSlot.RING_LEFT;
            }

            if (equippedItems.get(EquipmentSlot.RING_RIGHT) == null) {
                return EquipmentSlot.RING_RIGHT;
            }

            return EquipmentSlot.RING_LEFT;
        }

        private boolean isValidInventoryIndex(int index) {
            return index >= 0 && index < items.length;
        }

        private Item findStack(String itemName) {
            if (itemName == null || itemName.isBlank()) {
                return null;
            }

            for (Item item : items) {
                if (item != null && item.isStackable() && itemName.equalsIgnoreCase(item.getName())) {
                    return item;
                }
            }

            return null;
        }
    }

    public static class InventoryPanel {
        private static final int SLOT_SIZE = 46;
        private static final int SLOT_GAP = 6;
        private static final int GRID_MARGIN_BOTTOM = 20;

        private static final int EQUIPMENT_SLOT_SIZE = 46;
        private static final int EQUIPMENT_GAP = 10;
        private static final int EQUIPMENT_TO_GRID_GAP = 18;
        private static final int STAT_PREVIEW_HEIGHT = 132;
        private static final int PAPER_DOLL_PANEL_SIZE = 132;
        private static final int PAPER_DOLL_IMAGE_SIZE = 96;
        private static final int LIMB_PANEL_WIDTH = 360;
        private static final int LIMB_PANEL_HEIGHT = 116;

        private final Inventory inventory;
        private final GameState gameState;
        private final SoundSystem soundSystem;
        private final PaperDollRenderer paperDollRenderer = new PaperDollRenderer();
        private EquipmentSlot draggedEquipmentSlot = null;

        private final Rectangle[] inventorySlotBounds = new Rectangle[Inventory.SLOT_COUNT];
        private final Map<EquipmentSlot, Rectangle> equipmentSlotBounds = new EnumMap<>(EquipmentSlot.class);
        private final List<ContextMenuOption> contextMenuOptions = new ArrayList<>();
        private final Rectangle contextMenuBounds = new Rectangle();
        private final Rectangle paperDollBounds = new Rectangle();
        private String examineTooltipTitle;
        private String examineTooltipText;
        private Point examineTooltipPoint;
        private final Rectangle examineTooltipBounds = new Rectangle();
        private final Rectangle inventoryTabBounds = new Rectangle();
        private final Rectangle formationTabBounds = new Rectangle();
        private final Rectangle autoArrangeBounds = new Rectangle();
        private final Map<PartyFormation.Cell, Rectangle> formationCellBounds =
                new EnumMap<>(PartyFormation.Cell.class);
        private boolean formationTabSelected;
        private String draggedFormationMember;

        private Item draggedItem;
        private int draggedInventoryIndex = -1;
        private int contextInventoryIndex = -1;
        private Point mousePoint;

        public InventoryPanel(Inventory inventory) {
            this(inventory, null, null);
        }

        public InventoryPanel(Inventory inventory, GameState gameState, SoundSystem soundSystem) {
            this.inventory = inventory;
            this.gameState = gameState;
            this.soundSystem = soundSystem;
        }

        private Inventory inventory() {
            return gameState == null ? inventory : gameState.getInventory();
        }

        public void draw(Graphics2D g, int panelWidth, int panelHeight) {
            if (formationTabSelected) {
                drawCharacterTabs(g, panelWidth);
                drawFormationEditor(g, panelWidth, panelHeight);
                return;
            }
            calculateBounds(panelWidth, panelHeight);

            drawStatPreview(g);
            drawPaperDollPreview(g);
            drawLimbPanel(g, panelWidth);
            drawEquipmentSlots(g);
            drawInventoryGrid(g);
            drawContextMenu(g);
            drawHoverTooltip(g, panelWidth, panelHeight);
            drawExamineTooltip(g, panelWidth, panelHeight);
            drawDraggedItem(g);
            drawCharacterTabs(g, panelWidth);
        }

        public boolean handleMousePressed(MouseEvent e) {
            mousePoint = e.getPoint();

            if (inventoryTabBounds.contains(mousePoint)) {
                formationTabSelected = false; clearDrag(); return true;
            }
            if (formationTabBounds.contains(mousePoint)) {
                formationTabSelected = true; clearDrag(); return true;
            }
            if (formationTabSelected) return handleFormationPressed(mousePoint);

            if (handleExamineTooltipClick(mousePoint)) {
                return true;
            }

            if (handleContextMenuClick(mousePoint)) {
                return true;
            }

            if (contextInventoryIndex >= 0) {
                closeContextMenu();
                clearExamineTooltip();
                return true;
            }

            int inventoryIndex = getInventorySlotAt(mousePoint);

            if (inventoryIndex >= 0) {
                Item item = inventory().getItem(inventoryIndex);

                if (item == null) {
                    return false;
                }

                if (SwingUtilities.isRightMouseButton(e)) {
                    openContextMenu(inventoryIndex, mousePoint);
                    clearDrag();
                    return true;
                }

                if (e.getClickCount() >= 2) {
                    if (item.isEquippable()) {
                        equipInventoryItem(inventoryIndex);
                        clearDrag();
                        return true;
                    }
                    if (!useItem(inventoryIndex)) {
                        EquipmentSlot slot = inventory().getPreferredEquipmentSlot(item);
                        if (canWearItem(item, slot)) {
                            inventory().equipFromInventory(inventoryIndex);
                        }
                    }
                    clearDrag();
                    return true;
                }

                draggedItem = item;
                draggedInventoryIndex = inventoryIndex;
                draggedEquipmentSlot = null;
                return true;
            }

            EquipmentSlot equipmentSlot = getEquipmentSlotAt(mousePoint);

            if (equipmentSlot != null) {
                Item equippedItem = inventory().getEquippedItem(equipmentSlot);

                if (equippedItem == null) {
                    return false;
                }

                if (e.getClickCount() >= 2) {
                    inventory().unequipToInventory(equipmentSlot);
                    clearDrag();
                    return true;
                }

                draggedItem = equippedItem;
                draggedInventoryIndex = -1;
                draggedEquipmentSlot = equipmentSlot;
                return true;
            }

            return false;
        }

        private void openContextMenu(int inventoryIndex, Point point) {
            contextInventoryIndex = inventoryIndex;
            contextMenuOptions.clear();

            Item item = inventory().getItem(inventoryIndex);

            if (item == null) {
                return;
            }

            if (item.isEquippable()) {
                contextMenuOptions.add(new ContextMenuOption("Wear", () -> {
                    equipInventoryItem(contextInventoryIndex);
                    closeContextMenu();
                }));
            }

            if (item instanceof LimbItem limb && gameState != null) {
                contextMenuOptions.add(new ContextMenuOption("Graft", () -> {
                    int indexToRemove = contextInventoryIndex;
                    gameState.openInteraction(InteractionSystem.graftMenu(
                            gameState,
                            limb,
                            () -> inventory().removeItem(indexToRemove)
                    ));
                    closeContextMenu();
                }));
            }

            contextMenuOptions.add(new ContextMenuOption("Examine", () -> {
                showExamineTooltip(item);
                closeContextMenu();
            }));

            contextMenuOptions.add(new ContextMenuOption("Use", () -> {
                selectItemForWorldUse(contextInventoryIndex);
                closeContextMenu();
            }));

            if (gameState != null && gameState.hasSingleIngredientCraftingRecipe(inventoryIndex)) {
                contextMenuOptions.add(new ContextMenuOption("Craft", () -> {
                    gameState.openSingleIngredientCraftingMenu(contextInventoryIndex);
                    closeContextMenu();
                }));
            }

            contextMenuOptions.add(new ContextMenuOption("Drop", () -> {
                dropItem(contextInventoryIndex);
                closeContextMenu();
            }));

            int width = 96;
            int height = contextMenuOptions.size() * 26 + 8;
            contextMenuBounds.setBounds(point.x, point.y, width, height);
        }

        private boolean handleContextMenuClick(Point point) {
            if (contextInventoryIndex < 0 || point == null) {
                return false;
            }

            if (!contextMenuBounds.contains(point)) {
                return false;
            }

            int optionY = contextMenuBounds.y + 4;

            for (ContextMenuOption option : contextMenuOptions) {
                Rectangle optionBounds = new Rectangle(contextMenuBounds.x + 4, optionY, contextMenuBounds.width - 8, 24);

                if (optionBounds.contains(point)) {
                    option.action().run();
                    return true;
                }

                optionY += 26;
            }

            return true;
        }

        private void closeContextMenu() {
            contextInventoryIndex = -1;
            contextMenuOptions.clear();
            contextMenuBounds.setBounds(0, 0, 0, 0);
        }

        private boolean equipInventoryItem(int inventoryIndex) {
            Item item = inventory().getItem(inventoryIndex);
            if (item == null || !item.isEquippable()) {
                return false;
            }

            EquipmentSlot slot = inventory().getPreferredEquipmentSlot(item);
            if (!canWearItem(item, slot)) {
                showEquipFailureTooltip(item, slot);
                return false;
            }

            return gameState == null
                    ? inventory().equipFromInventory(inventoryIndex)
                    : gameState.equipInventoryItem(inventoryIndex);
        }

        private void showEquipFailureTooltip(Item item, EquipmentSlot slot) {
            examineTooltipTitle = "Cannot equip";
            if (gameState == null || gameState.getPlayerCharacter() == null) {
                examineTooltipText = item == null
                        ? "That cannot be equipped."
                        : item.getName() + " cannot be equipped there.";
            } else {
                String reason = gameState.getPlayerCharacter().equipmentRestrictionMessage(item, slot);
                examineTooltipText = reason == null || reason.isBlank()
                        ? item.getName() + " cannot be equipped there."
                        : reason;
            }
            examineTooltipPoint = mousePoint == null ? new Point(24, 24) : new Point(mousePoint);
        }

        private boolean useItem(int inventoryIndex) {
            Item item = inventory().getItem(inventoryIndex);

            if (item == null) {
                return false;
            }

            if (gameState != null) {
                if (gameState.hasSelectedWorldUseItem()
                        && gameState.tryUseSelectedWorldItemOnInventoryItem(inventoryIndex)) {
                    return true;
                }
                gameState.selectWorldUseItem(inventoryIndex);
                return true;
            }

            if (item.getItemType() != ItemType.CONSUMABLE) {
                return false;
            }

            boolean used = false;

            if (item.getHealAmount() > 0 && gameState != null) {
                PlayerCharacter playerCharacter = gameState.getPlayerCharacter();
                int beforeHp = playerCharacter.getCurrHp();
                playerCharacter.heal(item.getHealAmount());
                used = playerCharacter.getCurrHp() > beforeHp;
            }

            if (!used) {
                return false;
            }

            if (soundSystem != null) {
                soundSystem.playSound(item.getUseSoundPath());
            }

            inventory().removeItem(inventoryIndex);
            return true;
        }

        private boolean selectItemForWorldUse(int inventoryIndex) {
            if (gameState == null || inventory().getItem(inventoryIndex) == null) {
                return false;
            }

            if (gameState.hasSelectedWorldUseItem()
                    && gameState.tryUseSelectedWorldItemOnInventoryItem(inventoryIndex)) {
                return true;
            }

            gameState.selectWorldUseItem(inventoryIndex);
            return true;
        }

        private boolean dropItem(int inventoryIndex) {
            if (gameState == null) {
                return false;
            }

            Item item = inventory().removeItem(inventoryIndex);

            if (item == null) {
                return false;
            }

            int dropX = gameState.getPlayerX() + forwardX(gameState.getDirection());
            int dropY = gameState.getPlayerY() + forwardY(gameState.getDirection());

            if (gameState.getDungeonMap() == null || !gameState.getDungeonMap().isWalkable(dropX, dropY)) {
                dropX = gameState.getPlayerX();
                dropY = gameState.getPlayerY();
            }

            gameState.addEntity(new MapEntity(item, dropX, dropY));
            return true;
        }

        private int forwardX(int direction) {
            return switch (direction) {
                case 1 -> 1;
                case 3 -> -1;
                default -> 0;
            };
        }

        private int forwardY(int direction) {
            return switch (direction) {
                case 0 -> -1;
                case 2 -> 1;
                default -> 0;
            };
        }

        public boolean handleMouseDragged(MouseEvent e) {
            if (draggedFormationMember != null) { mousePoint = e.getPoint(); return true; }
            if (draggedItem == null) {
                return false;
            }

            mousePoint = e.getPoint();
            return true;
        }

        public boolean handleMouseMoved(MouseEvent e) {
            if (e == null) {
                return false;
            }

            mousePoint = e.getPoint();
            return true;
        }

        public boolean handleMouseReleased(MouseEvent e) {
            if (draggedFormationMember != null) {
                mousePoint = e.getPoint();
                PartyFormation.Cell target = formationCellAt(mousePoint);
                if (target != null && gameState != null && !gameState.isBattleMode()) {
                    gameState.getPlayerCharacter().getPartyFormation().move(draggedFormationMember, target);
                }
                draggedFormationMember = null;
                return true;
            }
            if (draggedItem == null) {
                return false;
            }

            mousePoint = e.getPoint();

            if (draggedInventoryIndex >= 0) {
                return finishInventoryDrag();
            }

            if (draggedEquipmentSlot != null) {
                return finishEquipmentDrag();
            }

            clearDrag();
            return true;
        }

        private boolean finishInventoryDrag() {
            EquipmentSlot targetEquipmentSlot = getEquipmentSlotAt(mousePoint);

            if (targetEquipmentSlot != null) {
                if (canWearItem(draggedItem, targetEquipmentSlot)) {
                    inventory().equipFromInventory(draggedInventoryIndex, targetEquipmentSlot);
                } else {
                    showEquipFailureTooltip(draggedItem, targetEquipmentSlot);
                }
                clearDrag();
                return true;
            }

            if (paperDollBounds.contains(mousePoint)
                    && draggedItem != null
                    && draggedItem.isEquippable()) {
                equipInventoryItem(draggedInventoryIndex);
                clearDrag();
                return true;
            }

            int targetInventoryIndex = getInventorySlotAt(mousePoint);

            if (targetInventoryIndex >= 0) {
                inventory().swapInventorySlots(draggedInventoryIndex, targetInventoryIndex);
                clearDrag();
                return true;
            }

            clearDrag();
            return true;
        }

        private boolean finishEquipmentDrag() {
            EquipmentSlot targetEquipmentSlot = getEquipmentSlotAt(mousePoint);

            if (targetEquipmentSlot != null) {
                inventory().moveEquippedItem(draggedEquipmentSlot, targetEquipmentSlot);
                clearDrag();
                return true;
            }

            int targetInventoryIndex = getInventorySlotAt(mousePoint);

            if (targetInventoryIndex >= 0) {
                inventory().unequipToInventory(draggedEquipmentSlot, targetInventoryIndex);
                clearDrag();
                return true;
            }

            clearDrag();
            return true;
        }

        private void calculateBounds(int panelWidth, int panelHeight) {
            int gridWidth = Inventory.GRID_COLUMNS * SLOT_SIZE
                    + (Inventory.GRID_COLUMNS - 1) * SLOT_GAP;

            int gridHeight = Inventory.GRID_ROWS * SLOT_SIZE
                    + (Inventory.GRID_ROWS - 1) * SLOT_GAP;

            int gridX = (panelWidth - gridWidth) / 2;
            int gridY = panelHeight - gridHeight - GRID_MARGIN_BOTTOM;

            for (int row = 0; row < Inventory.GRID_ROWS; row++) {
                for (int col = 0; col < Inventory.GRID_COLUMNS; col++) {
                    int index = row * Inventory.GRID_COLUMNS + col;

                    int x = gridX + col * (SLOT_SIZE + SLOT_GAP);
                    int y = gridY + row * (SLOT_SIZE + SLOT_GAP);

                    inventorySlotBounds[index] = new Rectangle(x, y, SLOT_SIZE, SLOT_SIZE);
                }
            }

            calculateEquipmentBounds(panelWidth, gridY);
            int paperDollX = 24;
            int paperDollY = 24 + STAT_PREVIEW_HEIGHT + 12;
            paperDollBounds.setBounds(paperDollX, paperDollY, PAPER_DOLL_PANEL_SIZE, PAPER_DOLL_PANEL_SIZE);
        }

        private void drawCharacterTabs(Graphics2D g, int panelWidth) {
            int width = 126, height = 30, y = 10;
            inventoryTabBounds.setBounds(panelWidth / 2 - width - 3, y, width, height);
            formationTabBounds.setBounds(panelWidth / 2 + 3, y, width, height);
            drawTab(g, inventoryTabBounds, "Inventory", !formationTabSelected);
            drawTab(g, formationTabBounds, "Formation", formationTabSelected);
        }

        private void drawTab(Graphics2D g, Rectangle bounds, String label, boolean selected) {
            g.setColor(selected ? new Color(73, 80, 94, 235) : new Color(28, 31, 38, 220));
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
            g.setColor(selected ? new Color(225, 198, 120) : new Color(125, 132, 146));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(label, bounds.x + (bounds.width - metrics.stringWidth(label)) / 2,
                    bounds.y + (bounds.height + metrics.getAscent()) / 2 - 2);
        }

        private void drawFormationEditor(Graphics2D g, int panelWidth, int panelHeight) {
            formationCellBounds.clear();
            if (gameState == null || gameState.getPlayerCharacter() == null) return;
            PlayerCharacter player = gameState.getPlayerCharacter();
            PartyFormation formation = player.getPartyFormation();
            int cellWidth = 142, cellHeight = 86, gap = 14;
            int totalWidth = cellWidth * 3 + gap * 2;
            int startX = (panelWidth - totalWidth) / 2;
            int startY = Math.max(70, panelHeight / 2 - cellHeight - gap / 2);
            for (PartyFormation.Cell cell : PartyFormation.Cell.values()) {
                int row = cell.isBack() ? 0 : 1;
                int x = startX + cell.column() * (cellWidth + gap);
                int y = startY + row * (cellHeight + gap);
                Rectangle bounds = new Rectangle(x, y, cellWidth, cellHeight);
                formationCellBounds.put(cell, bounds);
                boolean supported = !cell.isBack() || formation.memberAt(cell.supportingFrontCell()) != null;
                String memberId = formation.memberAt(cell);
                g.setColor(supported ? new Color(34, 39, 48, 225) : new Color(20, 22, 27, 205));
                g.fillRoundRect(x, y, cellWidth, cellHeight, 10, 10);
                g.setColor(supported ? new Color(130, 144, 165) : new Color(70, 74, 82));
                g.drawRoundRect(x, y, cellWidth, cellHeight, 10, 10);
                g.setColor(new Color(155, 160, 170));
                g.drawString((cell.isBack() ? "Back " : "Front ") + (cell.column() + 1), x + 9, y + 18);
                if (!supported) {
                    g.setColor(new Color(115, 116, 120)); g.drawString("Locked: fill front", x + 9, y + 48);
                } else if (memberId != null) {
                    PartyRoster.Member member = player.getPartyRoster().get(memberId);
                    String name = member == null ? memberId : member.displayName();
                    g.setColor(new Color(235, 225, 195));
                    g.setFont(g.getFont().deriveFont(Font.BOLD)); g.drawString(name, x + 12, y + 51);
                    g.setFont(g.getFont().deriveFont(Font.PLAIN));
                }
            }
            autoArrangeBounds.setBounds(panelWidth / 2 - 75, startY + cellHeight * 2 + gap + 24, 150, 34);
            g.setColor(new Color(45, 52, 64, 235)); g.fillRoundRect(autoArrangeBounds.x, autoArrangeBounds.y,
                    autoArrangeBounds.width, autoArrangeBounds.height, 8, 8);
            g.setColor(new Color(205, 184, 116)); g.drawRoundRect(autoArrangeBounds.x, autoArrangeBounds.y,
                    autoArrangeBounds.width, autoArrangeBounds.height, 8, 8);
            g.drawString("Auto Arrange", autoArrangeBounds.x + 31, autoArrangeBounds.y + 22);
            g.setColor(new Color(180, 184, 192));
            g.drawString("Drag or swap members. Back cells require the matching front cell.",
                    Math.max(20, panelWidth / 2 - 235), startY - 18);
        }

        private boolean handleFormationPressed(Point point) {
            if (gameState == null || gameState.getPlayerCharacter() == null || gameState.isBattleMode()) return true;
            PlayerCharacter player = gameState.getPlayerCharacter();
            if (autoArrangeBounds.contains(point)) {
                player.getPartyFormation().autoArrange(player.getPartyRoster().memberIds()); return true;
            }
            PartyFormation.Cell cell = formationCellAt(point);
            if (cell != null) draggedFormationMember = player.getPartyFormation().memberAt(cell);
            return true;
        }

        private PartyFormation.Cell formationCellAt(Point point) {
            if (point == null) return null;
            return formationCellBounds.entrySet().stream()
                    .filter(entry -> entry.getValue().contains(point))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
        }

        private void calculateEquipmentBounds(int panelWidth, int gridY) {
            EquipmentSlot[] slots = EquipmentSlot.values();

            int totalWidth = slots.length * EQUIPMENT_SLOT_SIZE
                    + (slots.length - 1) * EQUIPMENT_GAP;

            int startX = (panelWidth - totalWidth) / 2;
            int y = gridY - EQUIPMENT_SLOT_SIZE - EQUIPMENT_TO_GRID_GAP;

            equipmentSlotBounds.clear();

            for (int i = 0; i < slots.length; i++) {
                EquipmentSlot slot = slots[i];

                int x = startX + i * (EQUIPMENT_SLOT_SIZE + EQUIPMENT_GAP);

                equipmentSlotBounds.put(
                        slot,
                        new Rectangle(x, y, EQUIPMENT_SLOT_SIZE, EQUIPMENT_SLOT_SIZE)
                );
            }
        }

        private void drawInventoryGrid(Graphics2D g) {
            for (int i = 0; i < inventorySlotBounds.length; i++) {
                Rectangle bounds = inventorySlotBounds[i];

                if (bounds == null) {
                    continue;
                }

                drawSlot(g, bounds, null);

                Item item = inventory().getItem(i);

                if (item != null && i != draggedInventoryIndex) {
                    drawItem(g, item, bounds);
                }
            }
        }

        private void drawEquipmentSlots(Graphics2D g) {
            for (Map.Entry<EquipmentSlot, Rectangle> entry : equipmentSlotBounds.entrySet()) {
                EquipmentSlot slot = entry.getKey();
                Rectangle bounds = entry.getValue();

                drawSlot(g, bounds, slot.getLabel());

                Item equippedItem = inventory().getEquippedItem(slot);

                if (equippedItem != null && slot != draggedEquipmentSlot) {
                    drawItem(g, equippedItem, bounds);
                }

                if (draggedItem != null && canWearItem(draggedItem, slot)) {
                    drawValidEquipmentHint(g, bounds);
                }
            }
        }

        private boolean canWearItem(Item item, EquipmentSlot slot) {
            if (item == null || slot == null || !inventory().canEquipToSlot(item, slot)) {
                return false;
            }

            if (gameState == null || gameState.getPlayerCharacter() == null) {
                return true;
            }

            return gameState.getPlayerCharacter().canUseEquipment(item, slot);
        }

        private void drawContextMenu(Graphics2D g) {
            if (contextInventoryIndex < 0 || contextMenuOptions.isEmpty()) {
                return;
            }

            Composite oldComposite = g.getComposite();
            Font oldFont = g.getFont();

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
            g.setColor(new Color(8, 9, 13));
            g.fillRoundRect(
                    contextMenuBounds.x,
                    contextMenuBounds.y,
                    contextMenuBounds.width,
                    contextMenuBounds.height,
                    8,
                    8
            );
            g.setComposite(oldComposite);

            g.setColor(new Color(112, 92, 58));
            g.drawRoundRect(
                    contextMenuBounds.x,
                    contextMenuBounds.y,
                    contextMenuBounds.width,
                    contextMenuBounds.height,
                    8,
                    8
            );

            g.setFont(oldFont.deriveFont(Font.BOLD, 13f));
            int y = contextMenuBounds.y + 21;

            for (ContextMenuOption option : contextMenuOptions) {
                g.setColor(new Color(238, 228, 190));
                g.drawString(option.label(), contextMenuBounds.x + 12, y);
                y += 26;
            }

            g.setFont(oldFont);
        }

        private void showExamineTooltip(Item item) {
            examineTooltipTitle = item == null ? "Examine" : item.getName();
            examineTooltipText = item instanceof LimbItem limb
                    ? limbExamineText(limb)
                    : itemExamineText(item);
            examineTooltipPoint = mousePoint == null ? new Point(24, 24) : new Point(mousePoint);
        }

        private void clearExamineTooltip() {
            examineTooltipTitle = null;
            examineTooltipText = null;
            examineTooltipPoint = null;
            examineTooltipBounds.setBounds(0, 0, 0, 0);
        }

        private boolean handleExamineTooltipClick(Point point) {
            if (examineTooltipText == null || point == null || examineTooltipBounds.isEmpty()) {
                return false;
            }

            if (!examineTooltipBounds.contains(point)) {
                return false;
            }

            clearExamineTooltip();
            return true;
        }

        private String itemExamineText(Item item) {
            if (item == null) {
                return "There is nothing to examine.";
            }
            if (item.getItemType() != ItemType.WEAPON) {
                return item.getExamineText();
            }
            return item.getExamineText()
                    + "\n\nType: " + item.getWeaponType().getDisplayName()
                    + "\nHands: " + (item.isTwoHanded() ? "Two-handed" : "One-handed")
                    + "\nAccuracy: " + item.getWeaponAccuracyBonus()
                    + "\nPower: " + item.getWeaponPowerBonus()
                    + "\nMagic Accuracy: " + item.getMagicAccuracyBonus()
                    + "\nMagic Power: " + item.getMagicPowerBonus()
                    + "\nSpeed: " + Math.round(item.getWeaponSpeedMultiplier() * 100.0) + "% interval";
        }

        private String limbExamineText(LimbItem limb) {
            PlayerCharacter player = gameState == null ? null : gameState.getPlayerCharacter();
            LimbItem currentLimb = player == null ? null : player.getEquippedLimb(limb.getLimbSlot());
            StringBuilder builder = new StringBuilder();

            if (limb.getExamineText() != null && !limb.getExamineText().isBlank()) {
                builder.append(limb.getExamineText().trim()).append("\n\n");
            }

            builder.append(limb.getName())
                    .append(" was cut from ")
                    .append(limb.getSourceCreatureName() == null || limb.getSourceCreatureName().isBlank()
                            ? "an unknown creature"
                            : limb.getSourceCreatureName())
                    .append(".\nCondition: ")
                    .append(limb.getCondition().getDisplayName())
                    .append("\nSlot: ")
                    .append(limb.getLimbSlot().getDisplayName())
                    .append("\n\nStats vs current limb:");

            for (PlayerStat stat : PlayerStat.values()) {
                int next = limb.getEffectiveStat(stat);
                int current = currentLimb == null ? 0 : currentLimb.getEffectiveStat(stat);

                if (next != 0 || current != 0) {
                    int delta = next - current;
                    builder.append("\n")
                            .append(stat.getDisplayName())
                            .append(" ")
                            .append(next)
                            .append(delta == 0 ? "" : " (" + (delta > 0 ? "+" : "") + delta + ")");
                }
            }

            if (limb.getSkills().isEmpty()) {
                builder.append("\n\nAbilities: None");
            } else {
                builder.append("\n\nAbilities:");

                for (var skill : limb.getSkills()) {
                    builder.append("\n+ ").append(skill.getName());
                }
            }

            return builder.toString();
        }

        private void drawLimbPanel(Graphics2D g, int panelWidth) {
            if (gameState == null || gameState.getPlayerCharacter() == null) {
                return;
            }

            PlayerCharacter player = gameState.getPlayerCharacter();
            int x = Math.max(18, panelWidth - LIMB_PANEL_WIDTH - 24);
            int y = 24;

            Composite oldComposite = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f));
            g.setColor(new Color(8, 9, 13));
            g.fillRoundRect(x, y, LIMB_PANEL_WIDTH, LIMB_PANEL_HEIGHT, 8, 8);
            g.setComposite(oldComposite);

            g.setColor(new Color(112, 92, 58));
            g.drawRoundRect(x, y, LIMB_PANEL_WIDTH, LIMB_PANEL_HEIGHT, 8, 8);

            Font oldFont = g.getFont();
            g.setFont(oldFont.deriveFont(Font.BOLD, 14f));
            g.setColor(new Color(238, 228, 190));
            g.drawString("Grafted Limbs", x + 14, y + 24);

            g.setFont(oldFont.deriveFont(Font.PLAIN, 12f));
            int lineY = y + 46;

            for (LimbSlot slot : LimbSlot.values()) {
                LimbItem limb = player.getEquippedLimb(slot);
                String text = slot.getDisplayName() + ": "
                        + (limb == null ? "None" : limb.getName() + " (" + limb.getCondition().getDisplayName() + ")");
                g.setColor(limb == null || limb.isBroken() ? new Color(224, 110, 100) : new Color(210, 204, 178));
                g.drawString(text, x + 14, lineY);
                lineY += 14;
            }

            g.setFont(oldFont);
        }

        private void drawPaperDollPreview(Graphics2D g) {
            if (gameState == null || gameState.getPlayerCharacter() == null) {
                return;
            }

            int x = paperDollBounds.x;
            int y = paperDollBounds.y;

            Composite oldComposite = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f));
            g.setColor(new Color(8, 9, 13));
            g.fillRoundRect(x, y, PAPER_DOLL_PANEL_SIZE, PAPER_DOLL_PANEL_SIZE, 8, 8);
            g.setComposite(oldComposite);

            g.setColor(new Color(112, 92, 58));
            g.drawRoundRect(x, y, PAPER_DOLL_PANEL_SIZE, PAPER_DOLL_PANEL_SIZE, 8, 8);

            Font oldFont = g.getFont();
            g.setFont(oldFont.deriveFont(Font.BOLD, 14f));
            g.setColor(new Color(238, 228, 190));
            g.drawString("Body", x + 14, y + 24);

            BufferedImage paperDoll = paperDollRenderer.render(gameState.getPlayerCharacter());
            if (paperDoll != null) {
                Object oldHint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                int imageX = x + (PAPER_DOLL_PANEL_SIZE - PAPER_DOLL_IMAGE_SIZE) / 2;
                int imageY = y + 30;
                g.drawImage(paperDoll, imageX, imageY, PAPER_DOLL_IMAGE_SIZE, PAPER_DOLL_IMAGE_SIZE, null);
                if (oldHint != null) {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldHint);
                }
            }

            g.setFont(oldFont);
        }

        private void drawStatPreview(Graphics2D g) {
            if (gameState == null) {
                return;
            }

            PlayerCharacter player = gameState.getPlayerCharacter();
            int currentAttack = player.getUsableWeaponAccuracyBonus() + player.getEquipmentStatBonus(PlayerStat.ATTACK);
            int currentDamage = player.getUsableWeaponPowerBonus() + player.getEquipmentStatBonus(PlayerStat.STRENGTH);
            int currentDefense = player.getUsableArmorStatBonus() + player.getEquipmentStatBonus(PlayerStat.DEFENSE);
            int currentSpellcasting = player.getEquipmentStatBonus(PlayerStat.INTELLIGENCE)
                    + player.getUsableMagicAccuracyBonus()
                    + player.getUsableWeaponMagicAccuracyBonus();
            int currentPotency = player.getEquipmentStatBonus(PlayerStat.WILLPOWER)
                    + player.getUsableWeaponMagicPowerBonus();
            int currentSpeed = (int) Math.round(player.getUsableWeaponSpeedMultiplier() * 100.0);
            int previewAttack = currentAttack;
            int previewDamage = currentDamage;
            int previewDefense = currentDefense;
            int previewSpellcasting = currentSpellcasting;
            int previewPotency = currentPotency;
            int previewSpeed = currentSpeed;

            if (draggedItem != null && draggedItem.isEquippable()) {
                if (draggedItem.getItemType() == ItemType.WEAPON) {
                    Item currentWeapon = inventory().getEquippedItem(EquipmentSlot.WEAPON);
                    int currentWeaponAccuracy = currentWeapon == null ? 0 : currentWeapon.getWeaponAccuracyBonus();
                    int currentWeaponPower = currentWeapon == null ? 0 : currentWeapon.getWeaponPowerBonus();
                    int currentWeaponMagicAccuracy = currentWeapon == null ? 0 : currentWeapon.getMagicAccuracyBonus();
                    int currentWeaponMagicPower = currentWeapon == null ? 0 : currentWeapon.getMagicPowerBonus();
                    previewAttack = currentAttack - currentWeaponAccuracy + draggedItem.getWeaponAccuracyBonus();
                    previewDamage = currentDamage - currentWeaponPower + draggedItem.getWeaponPowerBonus();
                    previewSpellcasting = currentSpellcasting - currentWeaponMagicAccuracy + draggedItem.getMagicAccuracyBonus();
                    previewPotency = currentPotency - currentWeaponMagicPower + draggedItem.getMagicPowerBonus();
                    previewSpeed = (int) Math.round(draggedItem.getWeaponSpeedMultiplier() * 100.0);
                } else {
                    EquipmentSlot slot = previewSlotForItem(draggedItem);
                    Item currentEquipped = slot == null ? null : inventory().getEquippedItem(slot);
                    int equippedBonus = currentEquipped == null ? 0 : currentEquipped.getEffectiveStatBonus();
                    int draggedBonus = canWearItem(draggedItem, slot) ? draggedItem.getEffectiveStatBonus() : 0;

                    if (draggedItem.getItemType() == ItemType.RING && draggedItem.getStatBonusTarget() == PlayerStat.ATTACK) {
                        previewAttack = currentAttack - equippedBonus + draggedBonus;
                    } else if (draggedItem.getItemType() == ItemType.RING && draggedItem.getStatBonusTarget() == PlayerStat.STRENGTH) {
                        previewDamage = currentDamage - equippedBonus + draggedBonus;
                    } else if (draggedItem.getItemType() == ItemType.RING && draggedItem.getStatBonusTarget() == PlayerStat.DEFENSE) {
                        previewDefense = currentDefense - equippedBonus + draggedBonus;
                    } else if (draggedItem.getItemType() == ItemType.RING && draggedItem.getStatBonusTarget() == PlayerStat.INTELLIGENCE) {
                        previewSpellcasting = currentSpellcasting - equippedBonus + draggedBonus;
                    } else if (draggedItem.getItemType() == ItemType.RING && draggedItem.getStatBonusTarget() == PlayerStat.WILLPOWER) {
                        previewPotency = currentPotency - equippedBonus + draggedBonus;
                    } else if (draggedItem.getItemType() == ItemType.RING) {
                        previewSpellcasting = currentSpellcasting - equippedBonus + draggedBonus;
                    } else {
                        previewDefense = currentDefense - equippedBonus + draggedBonus;
                    }
                }
            }

            int panelWidth = 340;
            int x = 24;
            int y = 24;

            Composite oldComposite = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f));
            g.setColor(new Color(8, 9, 13));
            g.fillRoundRect(x, y, panelWidth, STAT_PREVIEW_HEIGHT, 8, 8);
            g.setComposite(oldComposite);

            g.setColor(new Color(112, 92, 58));
            g.drawRoundRect(x, y, panelWidth, STAT_PREVIEW_HEIGHT, 8, 8);

            Font oldFont = g.getFont();
            g.setFont(oldFont.deriveFont(Font.BOLD, 14f));
            g.setColor(new Color(238, 228, 190));
            g.drawString("Equipment Stats", x + 14, y + 24);

            g.setFont(oldFont.deriveFont(Font.PLAIN, 13f));
            drawStatLine(g, "Accuracy", currentAttack, previewAttack, x + 14, y + 46);
            drawStatLine(g, "Damage", currentDamage, previewDamage, x + 174, y + 46);
            drawStatLine(g, "Defense", currentDefense, previewDefense, x + 14, y + 66);
            drawStatLine(g, "Spellcasting", currentSpellcasting, previewSpellcasting, x + 174, y + 66);
            drawStatLine(g, "Spell Potency", currentPotency, previewPotency, x + 14, y + 86);
            drawPercentLine(g, "Speed", currentSpeed, previewSpeed, x + 174, y + 86);
            g.setColor(new Color(178, 170, 148));
            g.drawString("Weapon speed is attack interval: lower is faster.", x + 14, y + 112);
            g.setFont(oldFont);
        }

        private void drawHoverTooltip(Graphics2D g, int panelWidth, int panelHeight) {
            if (examineTooltipText != null || mousePoint == null || draggedItem != null || contextInventoryIndex >= 0) {
                return;
            }

            Item hoveredItem = getHoveredItem();
            if (hoveredItem == null) {
                return;
            }

            Font oldFont = g.getFont();
            g.setFont(oldFont.deriveFont(Font.BOLD, 13f));
            FontMetrics metrics = g.getFontMetrics();
            String label = hoveredItem.getName() == null ? "Unknown" : hoveredItem.getName();
            int tooltipWidth = metrics.stringWidth(label) + 18;
            int tooltipHeight = 26;
            int x = Math.max(8, Math.min(panelWidth - tooltipWidth - 8, mousePoint.x + 12));
            int y = Math.max(8, Math.min(panelHeight - tooltipHeight - 8, mousePoint.y + 12));

            Composite oldComposite = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
            g.setColor(new Color(8, 9, 13));
            g.fillRoundRect(x, y, tooltipWidth, tooltipHeight, 7, 7);
            g.setComposite(oldComposite);

            g.setColor(new Color(112, 92, 58));
            g.drawRoundRect(x, y, tooltipWidth, tooltipHeight, 7, 7);
            g.setColor(new Color(238, 228, 190));
            g.drawString(label, x + 9, y + 18);
            g.setFont(oldFont);
        }

        private void drawExamineTooltip(Graphics2D g, int panelWidth, int panelHeight) {
            if (examineTooltipText == null || examineTooltipText.isBlank()) {
                examineTooltipBounds.setBounds(0, 0, 0, 0);
                return;
            }

            Font oldFont = g.getFont();
            g.setFont(oldFont.deriveFont(Font.PLAIN, 13f));
            FontMetrics metrics = g.getFontMetrics();

            int maxTextWidth = 320;
            List<String> lines = wrapText(metrics, examineTooltipText, maxTextWidth);
            String title = examineTooltipTitle == null || examineTooltipTitle.isBlank() ? "Examine" : examineTooltipTitle;

            int tooltipWidth = maxTextWidth + 24;
            int titleHeight = 22;
            int lineHeight = metrics.getHeight();
            int tooltipHeight = Math.min(panelHeight - 16, titleHeight + lines.size() * lineHeight + 22);
            int preferredX = examineTooltipPoint == null ? 24 : examineTooltipPoint.x + 12;
            int preferredY = examineTooltipPoint == null ? 24 : examineTooltipPoint.y + 12;
            int x = Math.max(8, Math.min(panelWidth - tooltipWidth - 8, preferredX));
            int y = Math.max(8, Math.min(panelHeight - tooltipHeight - 8, preferredY));
            examineTooltipBounds.setBounds(x, y, tooltipWidth, tooltipHeight);

            Composite oldComposite = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.94f));
            g.setColor(new Color(8, 9, 13));
            g.fillRoundRect(x, y, tooltipWidth, tooltipHeight, 8, 8);
            g.setComposite(oldComposite);

            g.setColor(new Color(112, 92, 58));
            g.drawRoundRect(x, y, tooltipWidth, tooltipHeight, 8, 8);

            g.setFont(oldFont.deriveFont(Font.BOLD, 13f));
            g.setColor(new Color(238, 228, 190));
            g.drawString(title, x + 12, y + 18);

            g.setFont(oldFont.deriveFont(Font.PLAIN, 13f));
            g.setColor(new Color(218, 210, 180));
            int textY = y + titleHeight + metrics.getAscent();
            int maxLines = Math.max(1, (tooltipHeight - titleHeight - 16) / lineHeight);

            for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
                String line = i == maxLines - 1 && lines.size() > maxLines
                        ? trimTextToFit(metrics, lines.get(i) + "...", maxTextWidth)
                        : lines.get(i);
                g.drawString(line, x + 12, textY + i * lineHeight);
            }

            g.setFont(oldFont);
        }

        private List<String> wrapText(FontMetrics metrics, String text, int maxWidth) {
            List<String> lines = new ArrayList<>();

            for (String paragraph : text.split("\\R", -1)) {
                if (paragraph.isBlank()) {
                    lines.add("");
                    continue;
                }

                StringBuilder line = new StringBuilder();

                for (String word : paragraph.split("\\s+")) {
                    String candidate = line.isEmpty() ? word : line + " " + word;

                    if (metrics.stringWidth(candidate) <= maxWidth) {
                        line = new StringBuilder(candidate);
                    } else {
                        if (!line.isEmpty()) {
                            lines.add(line.toString());
                        }
                        line = new StringBuilder(word);
                    }
                }

                if (!line.isEmpty()) {
                    lines.add(line.toString());
                }
            }

            return lines;
        }

        private String trimTextToFit(FontMetrics metrics, String text, int maxWidth) {
            if (metrics.stringWidth(text) <= maxWidth) {
                return text;
            }

            String ellipsis = "...";

            for (int i = text.length() - 1; i >= 0; i--) {
                String candidate = text.substring(0, i) + ellipsis;

                if (metrics.stringWidth(candidate) <= maxWidth) {
                    return candidate;
                }
            }

            return ellipsis;
        }

        private Item getHoveredItem() {
            int inventoryIndex = getInventorySlotAt(mousePoint);
            if (inventoryIndex >= 0) {
                return inventory().getItem(inventoryIndex);
            }

            EquipmentSlot equipmentSlot = getEquipmentSlotAt(mousePoint);
            return equipmentSlot == null ? null : inventory().getEquippedItem(equipmentSlot);
        }

        private EquipmentSlot previewSlotForItem(Item item) {
            if (item == null) {
                return null;
            }

            return switch (item.getItemType()) {
                case HEAD_GEAR -> EquipmentSlot.HEAD;
                case CHEST_ARMOR -> EquipmentSlot.CHEST;
                case LEG_ARMOR -> EquipmentSlot.LEGS;
                case RING -> EquipmentSlot.RING_LEFT;
                case WEAPON -> EquipmentSlot.WEAPON;
                case SHIELD -> EquipmentSlot.SHIELD;
                case LIMB, MISC, CONSUMABLE -> null;
            };
        }

        private void drawStatLine(Graphics2D g, String label, int current, int preview, int x, int y) {
            int delta = preview - current;
            g.setColor(new Color(210, 204, 178));
            g.drawString(label + " " + current, x, y);

            if (delta == 0) {
                return;
            }

            g.setColor(delta > 0 ? new Color(92, 225, 112) : new Color(224, 74, 74));
            g.drawString((delta > 0 ? " +" : " ") + delta, x + 70, y);
        }

        private void drawPercentLine(Graphics2D g, String label, int current, int preview, int x, int y) {
            int delta = preview - current;
            g.setColor(new Color(210, 204, 178));
            g.drawString(label + " " + current + "%", x, y);

            if (delta == 0) {
                return;
            }

            g.setColor(delta < 0 ? new Color(92, 225, 112) : new Color(224, 74, 74));
            g.drawString((delta > 0 ? " +" : " ") + delta + "%", x + 70, y);
        }

        private void drawSlot(Graphics2D g, Rectangle bounds, String label) {
            Composite oldComposite = g.getComposite();

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.78f));
            g.setColor(new Color(20, 20, 24));
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f));
            g.setColor(new Color(210, 210, 210));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

            if (label != null) {
                Font oldFont = g.getFont();
                g.setFont(oldFont.deriveFont(10f));

                FontMetrics metrics = g.getFontMetrics();
                int textX = bounds.x + (bounds.width - metrics.stringWidth(label)) / 2;
                int textY = bounds.y - 4;

                g.setColor(Color.WHITE);
                g.drawString(label, textX, textY);

                g.setFont(oldFont);
            }

            g.setComposite(oldComposite);
        }

        private void drawItem(Graphics2D g, Item item, Rectangle bounds) {
            int padding = 5;

            int iconX = bounds.x + padding;
            int iconY = bounds.y + padding;
            int iconSize = bounds.width - padding * 2;

            if (item.getIcon() != null) {
                g.drawImage(
                        item.getIcon(),
                        iconX,
                        iconY,
                        iconSize,
                        iconSize,
                        null
                );
            } else {
                drawFallbackItemIcon(g, item, iconX, iconY, iconSize);
            }

            if (item.isStackable() && item.getQuantity() > 1) {
                drawStackQuantity(g, item.getQuantity(), bounds);
            }
        }

        private void drawStackQuantity(Graphics2D g, int quantity, Rectangle bounds) {
            String label = quantity >= 1_000_000
                    ? (quantity / 1_000_000) + "m"
                    : quantity >= 1_000
                    ? (quantity / 1_000) + "k"
                    : String.valueOf(quantity);

            Font oldFont = g.getFont();
            g.setFont(oldFont.deriveFont(Font.BOLD, 11f));
            FontMetrics metrics = g.getFontMetrics();
            int padding = 3;
            int textWidth = metrics.stringWidth(label);
            int badgeWidth = textWidth + padding * 2;
            int badgeHeight = metrics.getHeight();
            int x = bounds.x + bounds.width - badgeWidth - 3;
            int y = bounds.y + bounds.height - badgeHeight - 3;

            g.setColor(new Color(0, 0, 0, 180));
            g.fillRoundRect(x, y, badgeWidth, badgeHeight, 6, 6);
            g.setColor(new Color(255, 226, 99));
            g.drawString(label, x + padding, y + metrics.getAscent());
            g.setFont(oldFont);
        }

        private void drawFallbackItemIcon(Graphics2D g, Item item, int x, int y, int size) {
            g.setColor(getFallbackColor(item));
            g.fillRoundRect(x, y, size, size, 8, 8);

            g.setColor(Color.BLACK);
            g.drawRoundRect(x, y, size, size, 8, 8);

            String label = item.getName() == null || item.getName().isBlank()
                    ? "?"
                    : item.getName().substring(0, 1).toUpperCase();

            Font oldFont = g.getFont();
            g.setFont(oldFont.deriveFont(Font.BOLD, 18f));

            FontMetrics metrics = g.getFontMetrics();

            int textX = x + (size - metrics.stringWidth(label)) / 2;
            int textY = y + (size - metrics.getHeight()) / 2 + metrics.getAscent();

            g.setColor(Color.WHITE);
            g.drawString(label, textX, textY);

            g.setFont(oldFont);
        }

        private Color getFallbackColor(Item item) {
            return switch (item.getItemType()) {
                case HEAD_GEAR -> new Color(120, 120, 180);
                case CHEST_ARMOR -> new Color(130, 90, 70);
                case LEG_ARMOR -> new Color(90, 110, 150);
                case RING -> new Color(200, 170, 70);
                case WEAPON -> new Color(170, 170, 180);
                case SHIELD -> new Color(115, 145, 165);
                case LIMB -> new Color(180, 90, 120);
                case CONSUMABLE -> new Color(120, 180, 120);
                case MISC -> new Color(150, 150, 150);
            };
        }

        private void drawValidEquipmentHint(Graphics2D g, Rectangle bounds) {
            Stroke oldStroke = g.getStroke();
            Composite oldComposite = g.getComposite();

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f));
            g.setColor(Color.YELLOW);
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(
                    bounds.x - 3,
                    bounds.y - 3,
                    bounds.width + 6,
                    bounds.height + 6,
                    10,
                    10
            );

            g.setStroke(oldStroke);
            g.setComposite(oldComposite);
        }

        private void drawDraggedItem(Graphics2D g) {
            if (draggedItem == null || mousePoint == null) {
                return;
            }

            int size = SLOT_SIZE;
            int x = mousePoint.x - size / 2;
            int y = mousePoint.y - size / 2;

            Composite oldComposite = g.getComposite();

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
            drawItem(g, draggedItem, new Rectangle(x, y, size, size));

            g.setComposite(oldComposite);
        }

        public boolean hasActiveExamineInteraction() {
            return false;
        }

        public InteractionSystem.Interaction getActiveExamineInteraction() {
            return null;
        }

        public void closeExamineInteraction() {
            clearExamineTooltip();
        }

        private int getInventorySlotAt(Point point) {
            if (point == null) {
                return -1;
            }

            for (int i = 0; i < inventorySlotBounds.length; i++) {
                Rectangle bounds = inventorySlotBounds[i];

                if (bounds != null && bounds.contains(point)) {
                    return i;
                }
            }

            return -1;
        }

        private EquipmentSlot getEquipmentSlotAt(Point point) {
            if (point == null) {
                return null;
            }

            for (Map.Entry<EquipmentSlot, Rectangle> entry : equipmentSlotBounds.entrySet()) {
                if (entry.getValue().contains(point)) {
                    return entry.getKey();
                }
            }

            return null;
        }

        private void clearDrag() {
            draggedItem = null;
            draggedInventoryIndex = -1;
            draggedEquipmentSlot = null;
            mousePoint = null;
        }

        private record ContextMenuOption(String label, Runnable action) {
        }
    }
}
