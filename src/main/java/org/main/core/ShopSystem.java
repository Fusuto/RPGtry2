package org.main.core;

import org.main.content.ItemLibrary;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class ShopSystem {
    private ShopSystem() {
    }

    public enum ShopMode {
        BUY,
        SELL
    }

    public static ShopSession createBasicMerchantShop(String merchantName) {
        ShopSession shop = new ShopSession(
                merchantName == null || merchantName.isBlank()
                        ? "Merchant"
                        : merchantName
        );

        shop.addStock(ShopStockItem.fromItem(ItemLibrary.POTION.createItem(), -1));
        shop.addStock(ShopStockItem.fromItem(ItemLibrary.IRON_SWORD.createItem(), 3));
        shop.addStock(ShopStockItem.fromItem(ItemLibrary.LEATHER_CAP.createItem(), 2));
        shop.addStock(ShopStockItem.fromItem(ItemLibrary.SILVER_RING.createItem(), 1));

        return shop;
    }

    public static ShopSession createRandomMerchantShop(String merchantName, int stockCount) {
        ShopSession shop = new ShopSession(
                merchantName == null || merchantName.isBlank()
                        ? "Merchant"
                        : merchantName
        );
        Random random = new Random();
        ItemLibrary[] items = ItemLibrary.values();
        int count = Math.max(1, stockCount);

        for (int i = 0; i < count; i++) {
            ItemLibrary item = items[random.nextInt(items.length)];
            int quantity = item.getItemType() == InventorySystem.ItemType.CONSUMABLE
                    ? -1
                    : 1 + random.nextInt(3);
            shop.addStock(ShopStockItem.fromItem(item.createItem(), quantity));
        }

        return shop;
    }

    public static ShopSession createMerchantShop(String merchantName, List<ItemLibrary> setInventory) {
        if (setInventory == null || setInventory.isEmpty()) {
            return createRandomMerchantShop(merchantName, 4);
        }

        ShopSession shop = new ShopSession(
                merchantName == null || merchantName.isBlank()
                        ? "Merchant"
                        : merchantName
        );

        for (ItemLibrary item : setInventory) {
            if (item != null) {
                shop.addStock(ShopStockItem.fromItem(item.createItem(), 1));
            }
        }

        return shop;
    }

    public static class ShopStockItem {
        private final InventorySystem.Item item;
        private final int buyPrice;
        private final int sellPrice;

        /*
         * quantity:
         * -1 = infinite stock
         *  0 = sold out
         * >0 = limited stock
         */
        private int quantity;

        public ShopStockItem(
                InventorySystem.Item item,
                int buyPrice,
                int sellPrice,
                int quantity
        ) {
            this.item = item;
            this.buyPrice = Math.max(0, buyPrice);
            this.sellPrice = Math.max(0, sellPrice);
            this.quantity = quantity;
        }

        public static ShopStockItem fromItem(InventorySystem.Item item, int quantity) {
            if (item == null) {
                return new ShopStockItem(null, 0, 0, 0);
            }

            return new ShopStockItem(
                    item,
                    item.getCalculatedBuyPrice(),
                    item.getCalculatedSellPrice(),
                    quantity
            );
        }

        public InventorySystem.Item getItem() {
            return item;
        }

        public int getBuyPrice() {
            return buyPrice;
        }

        public int getSellPrice() {
            return sellPrice;
        }

        public int getQuantity() {
            return quantity;
        }

        public boolean hasStock() {
            return quantity == -1 || quantity > 0;
        }

        public boolean isInfiniteStock() {
            return quantity == -1;
        }

        public void decreaseQuantity() {
            if (quantity > 0) {
                quantity--;
            }
        }

        public InventorySystem.Item createInventoryItemCopy() {
            if (item == null) {
                return null;
            }

            return item.copy();
        }
    }

    public static class ShopSession {
        private final String shopName;
        private final List<ShopStockItem> stock = new ArrayList<>();

        private ShopMode mode = ShopMode.BUY;

        public ShopSession(String shopName) {
            this.shopName = shopName == null || shopName.isBlank()
                    ? "Shop"
                    : shopName;
        }

        public String getShopName() {
            return shopName;
        }

        public ShopMode getMode() {
            return mode;
        }

        public void setMode(ShopMode mode) {
            if (mode != null) {
                this.mode = mode;
            }
        }

        public List<ShopStockItem> getStock() {
            return stock;
        }

        public void addStock(ShopStockItem stockItem) {
            if (stockItem != null && stockItem.getItem() != null) {
                stock.add(stockItem);
            }
        }

        public boolean buyItem(int stockIndex, GameState gameState) {
            if (gameState == null || stockIndex < 0 || stockIndex >= stock.size()) {
                return false;
            }

            ShopStockItem stockItem = stock.get(stockIndex);

            if (stockItem == null || !stockItem.hasStock()) {
                return false;
            }

            if (!gameState.canSpendGold(stockItem.getBuyPrice())) {
                System.out.println("Not enough gold.");
                return false;
            }

            InventorySystem.Item purchasedItem = stockItem.createInventoryItemCopy();

            if (purchasedItem == null) {
                return false;
            }

            boolean added = gameState.getInventory().addItem(purchasedItem);

            if (!added) {
                System.out.println("Inventory full.");
                return false;
            }

            gameState.spendGold(stockItem.getBuyPrice());
            stockItem.decreaseQuantity();

            System.out.println("Bought " + purchasedItem.getName() + ".");
            return true;
        }

        public boolean sellInventoryItem(int inventoryIndex, GameState gameState) {
            if (gameState == null) {
                return false;
            }

            InventorySystem.Item soldItem = gameState.getInventory().removeItem(inventoryIndex);

            if (soldItem == null) {
                return false;
            }

            int sellPrice = getSellPriceForItem(soldItem);

            gameState.addGold(sellPrice);

            System.out.println("Sold " + soldItem.getName() + " for " + sellPrice + " gold.");
            return true;
        }

        public int getSellPriceForItem(InventorySystem.Item item) {
            if (item == null) {
                return 0;
            }

            for (ShopStockItem stockItem : stock) {
                if (stockItem == null || stockItem.getItem() == null) {
                    continue;
                }

                InventorySystem.Item stockItemData = stockItem.getItem();

                boolean sameName = stockItemData.getName().equals(item.getName());
                boolean sameType = stockItemData.getItemType() == item.getItemType();

                if (sameName && sameType) {
                    return stockItem.getSellPrice();
                }
            }

            return item.getCalculatedSellPrice();
        }

    }

    public static class ShopWindow {
        private static final int WINDOW_WIDTH = 760;
        private static final int WINDOW_HEIGHT = 430;
        private static final int WINDOW_MARGIN = 32;

        private static final int CLOSE_SIZE = 26;

        private static final int TAB_WIDTH = 90;
        private static final int TAB_HEIGHT = 30;

        private static final int ROW_HEIGHT = 42;
        private static final int ROW_GAP = 7;

        private static final int INVENTORY_GRID_COLUMNS = 5;
        private static final int INVENTORY_SLOT_SIZE = 46;
        private static final int INVENTORY_SLOT_GAP = 6;

        private final Map<Integer, Rectangle> buyItemBounds = new HashMap<>();
        private final Map<Integer, Rectangle> sellInventoryBounds = new HashMap<>();

        private Rectangle closeBounds = new Rectangle();
        private Rectangle buyTabBounds = new Rectangle();
        private Rectangle sellTabBounds = new Rectangle();

        public void draw(Graphics2D g, GameState gameState, int panelWidth, int panelHeight) {
            clearBounds();

            if (gameState == null || !gameState.hasActiveShop()) {
                return;
            }

            ShopSession shop = gameState.getActiveShop();

            if (shop == null) {
                return;
            }

            Object oldInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            );

            drawDimBackground(g, panelWidth, panelHeight);

            Rectangle windowBounds = calculateWindowBounds(panelWidth, panelHeight);

            drawWindowBackground(g, windowBounds);
            drawHeader(g, gameState, shop, windowBounds);
            drawTabs(g, shop, windowBounds);

            if (shop.getMode() == ShopMode.BUY) {
                drawBuyMode(g, gameState, shop, windowBounds);
            } else {
                drawSellMode(g, gameState, shop, windowBounds);
            }

            if (oldInterpolation != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
            }
        }

        public boolean handleMousePressed(MouseEvent e, GameState gameState) {
            if (gameState == null || !gameState.hasActiveShop()) {
                return false;
            }

            Point point = e.getPoint();
            ShopSession shop = gameState.getActiveShop();

            if (closeBounds.contains(point)) {
                gameState.closeShop();
                return true;
            }

            if (buyTabBounds.contains(point)) {
                shop.setMode(ShopMode.BUY);
                return true;
            }

            if (sellTabBounds.contains(point)) {
                shop.setMode(ShopMode.SELL);
                return true;
            }

            if (shop.getMode() == ShopMode.BUY) {
                for (Map.Entry<Integer, Rectangle> entry : buyItemBounds.entrySet()) {
                    if (entry.getValue().contains(point)) {
                        shop.buyItem(entry.getKey(), gameState);
                        return true;
                    }
                }
            }

            if (shop.getMode() == ShopMode.SELL) {
                for (Map.Entry<Integer, Rectangle> entry : sellInventoryBounds.entrySet()) {
                    if (entry.getValue().contains(point)) {
                        shop.sellInventoryItem(entry.getKey(), gameState);
                        return true;
                    }
                }
            }

            /*
             * Consume clicks while the shop is open so dungeon input does not
             * accidentally happen underneath the shop.
             */
            return true;
        }

        public boolean handleKeyPressed(KeyEvent e, GameState gameState) {
            if (gameState == null || !gameState.hasActiveShop()) {
                return false;
            }

            ShopSession shop = gameState.getActiveShop();

            switch (e.getKeyCode()) {
                case KeyEvent.VK_ESCAPE -> {
                    gameState.closeShop();
                    return true;
                }
                case KeyEvent.VK_B -> {
                    shop.setMode(ShopMode.BUY);
                    return true;
                }
                case KeyEvent.VK_S -> {
                    shop.setMode(ShopMode.SELL);
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        private void clearBounds() {
            buyItemBounds.clear();
            sellInventoryBounds.clear();

            closeBounds = new Rectangle();
            buyTabBounds = new Rectangle();
            sellTabBounds = new Rectangle();
        }

        private Rectangle calculateWindowBounds(int panelWidth, int panelHeight) {
            int width = Math.min(WINDOW_WIDTH, panelWidth - WINDOW_MARGIN * 2);
            int height = Math.min(WINDOW_HEIGHT, panelHeight - WINDOW_MARGIN * 2);

            int x = (panelWidth - width) / 2;
            int y = (panelHeight - height) / 2;

            return new Rectangle(x, y, width, height);
        }

        private void drawDimBackground(Graphics2D g, int panelWidth, int panelHeight) {
            Composite oldComposite = g.getComposite();

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.48f));
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, panelWidth, panelHeight);

            g.setComposite(oldComposite);
        }

        private void drawWindowBackground(Graphics2D g, Rectangle bounds) {
            Composite oldComposite = g.getComposite();

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.94f));
            g.setColor(new Color(18, 18, 24));
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 16, 16);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.98f));
            g.setColor(new Color(230, 230, 230));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 16, 16);

            g.setComposite(oldComposite);
        }

        private void drawHeader(
                Graphics2D g,
                GameState gameState,
                ShopSession shop,
                Rectangle windowBounds
        ) {
            Font oldFont = g.getFont();

            g.setFont(oldFont.deriveFont(Font.BOLD, 18f));
            g.setColor(Color.WHITE);
            g.drawString(shop.getShopName(), windowBounds.x + 22, windowBounds.y + 34);

            g.setFont(oldFont.deriveFont(Font.BOLD, 14f));
            String goldText = "Gold: " + gameState.getGold();
            FontMetrics metrics = g.getFontMetrics();

            int goldX = windowBounds.x + windowBounds.width - metrics.stringWidth(goldText) - 64;
            int goldY = windowBounds.y + 32;

            g.setColor(new Color(245, 215, 95));
            g.drawString(goldText, goldX, goldY);

            closeBounds = new Rectangle(
                    windowBounds.x + windowBounds.width - CLOSE_SIZE - 18,
                    windowBounds.y + 14,
                    CLOSE_SIZE,
                    CLOSE_SIZE
            );

            g.setColor(new Color(70, 70, 76));
            g.fillRect(closeBounds.x, closeBounds.y, closeBounds.width, closeBounds.height);

            g.setColor(Color.WHITE);
            g.drawRect(closeBounds.x, closeBounds.y, closeBounds.width, closeBounds.height);

            String closeText = "X";
            int closeTextX = closeBounds.x + (closeBounds.width - metrics.stringWidth(closeText)) / 2;
            int closeTextY = closeBounds.y
                    + (closeBounds.height - metrics.getHeight()) / 2
                    + metrics.getAscent();

            g.drawString(closeText, closeTextX, closeTextY);

            g.setFont(oldFont);
        }

        private void drawTabs(Graphics2D g, ShopSession shop, Rectangle windowBounds) {
            int tabY = windowBounds.y + 54;
            int tabX = windowBounds.x + 22;

            buyTabBounds = new Rectangle(tabX, tabY, TAB_WIDTH, TAB_HEIGHT);
            sellTabBounds = new Rectangle(tabX + TAB_WIDTH + 8, tabY, TAB_WIDTH, TAB_HEIGHT);

            drawTab(g, buyTabBounds, "Buy (B)", shop.getMode() == ShopMode.BUY);
            drawTab(g, sellTabBounds, "Sell (S)", shop.getMode() == ShopMode.SELL);
        }

        private void drawTab(Graphics2D g, Rectangle bounds, String label, boolean selected) {
            g.setColor(selected ? new Color(80, 80, 100) : new Color(42, 42, 52));
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

            g.setColor(Color.WHITE);
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

            FontMetrics metrics = g.getFontMetrics();

            int textX = bounds.x + (bounds.width - metrics.stringWidth(label)) / 2;
            int textY = bounds.y + (bounds.height - metrics.getHeight()) / 2 + metrics.getAscent();

            g.drawString(label, textX, textY);
        }

        private void drawBuyMode(
                Graphics2D g,
                GameState gameState,
                ShopSession shop,
                Rectangle windowBounds
        ) {
            int listX = windowBounds.x + 22;
            int listY = windowBounds.y + 100;
            int listWidth = windowBounds.width - 44;

            drawSectionLabelRightAligned(
                    g,
                    "Click an item to buy.",
                    windowBounds.x + windowBounds.width - 22,
                    listY - 12
            );

            List<ShopStockItem> stock = shop.getStock();

            for (int i = 0; i < stock.size(); i++) {
                ShopStockItem stockItem = stock.get(i);

                if (stockItem == null || stockItem.getItem() == null) {
                    continue;
                }

                int rowY = listY + i * (ROW_HEIGHT + ROW_GAP);

                if (rowY + ROW_HEIGHT > windowBounds.y + windowBounds.height - 28) {
                    break;
                }

                Rectangle rowBounds = new Rectangle(listX, rowY, listWidth, ROW_HEIGHT);
                buyItemBounds.put(i, rowBounds);

                boolean canAfford = gameState.canSpendGold(stockItem.getBuyPrice());
                boolean hasStock = stockItem.hasStock();

                drawBuyRow(g, stockItem, rowBounds, canAfford, hasStock);
            }
        }

        private void drawBuyRow(
                Graphics2D g,
                ShopStockItem stockItem,
                Rectangle bounds,
                boolean canAfford,
                boolean hasStock
        ) {
            Composite oldComposite = g.getComposite();

            if (!hasStock || !canAfford) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.50f));
            }

            g.setColor(new Color(38, 38, 48));
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

            g.setColor(new Color(205, 205, 215));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

            drawItemIcon(g, stockItem.getItem(), new Rectangle(bounds.x + 5, bounds.y + 5, 32, 32));

            g.setColor(Color.WHITE);

            String name = stockItem.getItem().getName();
            g.drawString(name, bounds.x + 48, bounds.y + 25);

            String details = getItemDetails(stockItem.getItem());

            if (!details.isBlank()) {
                Font oldFont = g.getFont();
                g.setFont(oldFont.deriveFont(Font.PLAIN, 10f));
                g.setColor(new Color(180, 180, 190));
                g.drawString(details, bounds.x + 48, bounds.y + 38);
                g.setFont(oldFont);
            }

            String price = stockItem.getBuyPrice() + "g";

            if (!stockItem.hasStock()) {
                price = "Sold out";
            } else if (!stockItem.isInfiniteStock()) {
                price += "  x" + stockItem.getQuantity();
            }

            FontMetrics metrics = g.getFontMetrics();
            int priceX = bounds.x + bounds.width - metrics.stringWidth(price) - 16;

            g.setColor(canAfford && hasStock ? new Color(245, 215, 95) : new Color(190, 110, 110));
            g.drawString(price, priceX, bounds.y + 25);

            g.setComposite(oldComposite);
        }

        private void drawSellMode(
                Graphics2D g,
                GameState gameState,
                ShopSession shop,
                Rectangle windowBounds
        ) {
            int gridWidth = INVENTORY_GRID_COLUMNS * INVENTORY_SLOT_SIZE
                    + (INVENTORY_GRID_COLUMNS - 1) * INVENTORY_SLOT_GAP;

            int gridX = windowBounds.x + (windowBounds.width - gridWidth) / 2;
            int gridY = windowBounds.y + 112;

            drawSectionLabelRightAligned(
                    g,
                    "Click an inventory item to sell.",
                    windowBounds.x + windowBounds.width - 22,
                    windowBounds.y + 88
            );

            for (int i = 0; i < InventorySystem.Inventory.SLOT_COUNT; i++) {
                int row = i / INVENTORY_GRID_COLUMNS;
                int col = i % INVENTORY_GRID_COLUMNS;

                int x = gridX + col * (INVENTORY_SLOT_SIZE + INVENTORY_SLOT_GAP);
                int y = gridY + row * (INVENTORY_SLOT_SIZE + INVENTORY_SLOT_GAP);

                Rectangle slotBounds = new Rectangle(
                        x,
                        y,
                        INVENTORY_SLOT_SIZE,
                        INVENTORY_SLOT_SIZE
                );

                drawInventorySlot(g, slotBounds);

                InventorySystem.Item item = gameState.getInventory().getItem(i);

                if (item == null) {
                    continue;
                }

                sellInventoryBounds.put(i, slotBounds);

                drawItemIcon(g, item, slotBounds);

                int sellPrice = shop.getSellPriceForItem(item);
                drawSmallPriceTag(g, sellPrice + "g", slotBounds);
            }
        }

        private void drawSectionLabelRightAligned(Graphics2D g, String label, int rightX, int y) {
            Font oldFont = g.getFont();

            g.setFont(oldFont.deriveFont(Font.BOLD, 13f));
            g.setColor(new Color(220, 220, 230));

            FontMetrics metrics = g.getFontMetrics();
            g.drawString(label, rightX - metrics.stringWidth(label), y);

            g.setFont(oldFont);
        }

        private void drawInventorySlot(Graphics2D g, Rectangle bounds) {
            g.setColor(new Color(24, 24, 32));
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

            g.setColor(new Color(205, 205, 215));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        }

        private void drawItemIcon(Graphics2D g, InventorySystem.Item item, Rectangle bounds) {
            if (item == null) {
                return;
            }

            int padding = 5;

            int iconX = bounds.x + padding;
            int iconY = bounds.y + padding;
            int iconSize = Math.min(bounds.width, bounds.height) - padding * 2;

            BufferedImage icon = item.getIcon();

            if (icon != null) {
                g.drawImage(icon, iconX, iconY, iconSize, iconSize, null);
                return;
            }

            drawFallbackIcon(g, item, iconX, iconY, iconSize);
        }

        private void drawFallbackIcon(
                Graphics2D g,
                InventorySystem.Item item,
                int x,
                int y,
                int size
        ) {
            g.setColor(getFallbackColor(item));
            g.fillRoundRect(x, y, size, size, 8, 8);

            g.setColor(Color.BLACK);
            g.drawRoundRect(x, y, size, size, 8, 8);

            String label = item.getName() == null || item.getName().isBlank()
                    ? "?"
                    : item.getName().substring(0, 1).toUpperCase();

            Font oldFont = g.getFont();
            g.setFont(oldFont.deriveFont(Font.BOLD, 16f));

            FontMetrics metrics = g.getFontMetrics();

            int textX = x + (size - metrics.stringWidth(label)) / 2;
            int textY = y + (size - metrics.getHeight()) / 2 + metrics.getAscent();

            g.setColor(Color.WHITE);
            g.drawString(label, textX, textY);

            g.setFont(oldFont);
        }

        private Color getFallbackColor(InventorySystem.Item item) {
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

        private String getItemDetails(InventorySystem.Item item) {
            if (item instanceof LimbItem limb) {
                return limb.getLimbSlot().getDisplayName()
                        + " / "
                        + limb.getCondition().getDisplayName();
            }

            if (item == null || !item.isEquippable()) {
                return "";
            }

            return item.getMaterial().getDisplayName()
                    + " / "
                    + item.getDurability().getDisplayName()
                    + " / +"
                    + item.getEffectiveStatBonus();
        }

        private void drawSmallPriceTag(Graphics2D g, String text, Rectangle slotBounds) {
            Font oldFont = g.getFont();

            g.setFont(oldFont.deriveFont(Font.BOLD, 10f));
            FontMetrics metrics = g.getFontMetrics();

            int tagWidth = metrics.stringWidth(text) + 8;
            int tagHeight = 15;

            int tagX = slotBounds.x + slotBounds.width - tagWidth - 2;
            int tagY = slotBounds.y + slotBounds.height - tagHeight - 2;

            Composite oldComposite = g.getComposite();
            Stroke oldStroke = g.getStroke();

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.84f));

            g.setColor(Color.BLACK);
            g.fillRoundRect(tagX, tagY, tagWidth, tagHeight, 6, 6);

            g.setColor(new Color(245, 215, 95));
            g.setStroke(new BasicStroke(1));
            g.drawRoundRect(tagX, tagY, tagWidth, tagHeight, 6, 6);

            int textX = tagX + 4;
            int textY = tagY + (tagHeight - metrics.getHeight()) / 2 + metrics.getAscent();

            g.drawString(text, textX, textY);

            g.setComposite(oldComposite);
            g.setStroke(oldStroke);
            g.setFont(oldFont);
        }
    }
}
