package org.main.core;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

public final class InventorySystem {
    private InventorySystem() {
    }

    public enum ItemType {
        HEAD_GEAR,
        CHEST_ARMOR,
        LEG_ARMOR,
        RING,
        WEAPON,
        MISC,
        CONSUMABLE
    }

    public enum EquipmentSlot {
        HEAD("Head"),
        CHEST("Chest"),
        LEGS("Legs"),
        RING_LEFT("Ring"),
        RING_RIGHT("Ring"),
        WEAPON("Weapon");

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

        public Item(String name, ItemType itemType, BufferedImage icon) {
            this.name = name;
            this.itemType = itemType;
            this.icon = icon;
        }

        public Item(String name, ItemType itemType, String iconPath) {
            this(name, itemType, loadIcon(iconPath));
        }

        private static BufferedImage loadIcon(String iconPath) {
            if (iconPath == null || iconPath.isBlank()) {
                return null;
            }

            try {
                File file = new File(iconPath);

                if (!file.exists()) {
                    System.out.println("Item icon not found: " + iconPath);
                    return null;
                }

                return ImageIO.read(file);
            } catch (IOException e) {
                System.out.println("Failed to load item icon: " + iconPath);
                e.printStackTrace();
                return null;
            }
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

        public boolean isEquippable() {
            return itemType == ItemType.HEAD_GEAR
                    || itemType == ItemType.CHEST_ARMOR
                    || itemType == ItemType.LEG_ARMOR
                    || itemType == ItemType.RING
                    || itemType == ItemType.WEAPON;
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

        public boolean addItem(Item item) {
            if (item == null) {
                return false;
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
            if (item == null || !isValidInventoryIndex(index) || items[index] != null) {
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

            equippedItems.put(targetSlot, item);
            items[inventoryIndex] = previouslyEquipped;

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
                case MISC, CONSUMABLE -> false;
            };
        }

        private EquipmentSlot getPreferredEquipmentSlot(Item item) {
            if (item == null) {
                return null;
            }

            return switch (item.getItemType()) {
                case HEAD_GEAR -> EquipmentSlot.HEAD;
                case CHEST_ARMOR -> EquipmentSlot.CHEST;
                case LEG_ARMOR -> EquipmentSlot.LEGS;
                case WEAPON -> EquipmentSlot.WEAPON;
                case RING -> getPreferredRingSlot();
                case MISC, CONSUMABLE -> null;
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
    }

    public static class InventoryPanel {
        private static final int SLOT_SIZE = 46;
        private static final int SLOT_GAP = 6;
        private static final int GRID_MARGIN_BOTTOM = 20;

        private static final int EQUIPMENT_SLOT_SIZE = 46;
        private static final int EQUIPMENT_GAP = 10;
        private static final int EQUIPMENT_TO_GRID_GAP = 18;

        private final Inventory inventory;
        private EquipmentSlot draggedEquipmentSlot = null;

        private final Rectangle[] inventorySlotBounds = new Rectangle[Inventory.SLOT_COUNT];
        private final Map<EquipmentSlot, Rectangle> equipmentSlotBounds = new EnumMap<>(EquipmentSlot.class);

        private Item draggedItem;
        private int draggedInventoryIndex = -1;
        private Point mousePoint;

        public InventoryPanel(Inventory inventory) {
            this.inventory = inventory;
        }

        public void draw(Graphics2D g, int panelWidth, int panelHeight) {
            calculateBounds(panelWidth, panelHeight);

            drawEquipmentSlots(g);
            drawInventoryGrid(g);
            drawDraggedItem(g);
        }

        public boolean handleMousePressed(MouseEvent e) {
            mousePoint = e.getPoint();

            int inventoryIndex = getInventorySlotAt(mousePoint);

            if (inventoryIndex >= 0) {
                Item item = inventory.getItem(inventoryIndex);

                if (item == null) {
                    return false;
                }

                if (e.getClickCount() >= 2) {
                    inventory.equipFromInventory(inventoryIndex);
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
                Item equippedItem = inventory.getEquippedItem(equipmentSlot);

                if (equippedItem == null) {
                    return false;
                }

                if (e.getClickCount() >= 2) {
                    inventory.unequipToInventory(equipmentSlot);
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

        public boolean handleMouseDragged(MouseEvent e) {
            if (draggedItem == null) {
                return false;
            }

            mousePoint = e.getPoint();
            return true;
        }

        public boolean handleMouseReleased(MouseEvent e) {
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
                inventory.equipFromInventory(draggedInventoryIndex, targetEquipmentSlot);
                clearDrag();
                return true;
            }

            int targetInventoryIndex = getInventorySlotAt(mousePoint);

            if (targetInventoryIndex >= 0) {
                inventory.swapInventorySlots(draggedInventoryIndex, targetInventoryIndex);
                clearDrag();
                return true;
            }

            clearDrag();
            return true;
        }

        private boolean finishEquipmentDrag() {
            EquipmentSlot targetEquipmentSlot = getEquipmentSlotAt(mousePoint);

            if (targetEquipmentSlot != null) {
                inventory.moveEquippedItem(draggedEquipmentSlot, targetEquipmentSlot);
                clearDrag();
                return true;
            }

            int targetInventoryIndex = getInventorySlotAt(mousePoint);

            if (targetInventoryIndex >= 0) {
                inventory.unequipToInventory(draggedEquipmentSlot, targetInventoryIndex);
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

                Item item = inventory.getItem(i);

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

                Item equippedItem = inventory.getEquippedItem(slot);

                if (equippedItem != null && slot != draggedEquipmentSlot) {
                    drawItem(g, equippedItem, bounds);
                }

                if (draggedItem != null && inventory.canEquipToSlot(draggedItem, slot)) {
                    drawValidEquipmentHint(g, bounds);
                }
            }
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
    }
}