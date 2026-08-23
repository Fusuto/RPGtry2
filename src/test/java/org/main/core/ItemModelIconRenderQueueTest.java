package org.main.core;

import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemModelIconRenderQueueTest {
    @Test
    void graphicsTransformMovesLiveModelIconIntoCompactWindowCoordinates() {
        InventorySystem.Item dagger = new InventorySystem.Item(
                "Dagger", InventorySystem.ItemType.WEAPON, (BufferedImage) null)
                .withFirstPersonModel("assets/dagger.glb");
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.translate(210, 85);

        ItemModelIconRenderQueue.beginCapture();
        assertTrue(ItemModelIconRenderQueue.request(graphics, dagger, 12, 18, 30, 30));
        List<ItemModelIconRenderQueue.Request> requests = ItemModelIconRenderQueue.finishCapture();
        graphics.dispose();

        assertEquals(1, requests.size());
        assertEquals(new Rectangle(222, 103, 30, 30), requests.get(0).bounds());
    }
}
