package org.main.core;

import org.junit.jupiter.api.Test;
import org.main.engine.AssetLoader;
import static org.junit.jupiter.api.Assertions.*;

class MaceIconTintTest {
    @Test void groundIconTierTintPreservesGripAndAlpha() {
        var source = AssetLoader.loadImage("assets/3D/weapons/mace/mace_icon.png");
        var mask = AssetLoader.loadImage("assets/3D/weapons/mace/mace_icon.tint-mask.png");
        assertNotNull(source);
        assertNotNull(mask);
        var tinted = InventorySystem.Item.maskedMaterialTint(source, mask, GearMaterial.COPPER);
        int changed = 0, preserved = 0;
        for (int y=0; y<source.getHeight(); y++) for (int x=0; x<source.getWidth(); x++) {
            int a = source.getRGB(x,y), b = tinted.getRGB(x,y);
            assertEquals(a >>> 24, b >>> 24);
            if ((a >>> 24) > 200 && ((mask.getRGB(x,y) >> 16) & 255) == 0) {
                assertEquals(a,b);
                preserved++;
            }
            if (a != b) changed++;
        }
        assertTrue(changed > 100);
        assertTrue(preserved > 100);
    }
}
