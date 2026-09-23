package org.main.experimental;

import org.junit.jupiter.api.Test;
import org.main.core.GearMaterial;
import java.awt.Color;
import static org.junit.jupiter.api.Assertions.*;

class MaceMaterialTest {
    @Test void tintOnlyChangesTaggedMetalAndSharesGeometry() {
        var metal = new LwjglStaticModel.Mesh(new float[9], new float[6], new int[]{0,1,2}, null,
                1,1,1,1,true);
        var handle = new LwjglStaticModel.Mesh(new float[9], new float[6], new int[]{0,1,2}, null,
                .2f,.1f,.05f,1,false);
        var tinted = LwjglStaticModel.tintMesh(metal, new Color(200,100,50), 1);
        assertSame(handle, LwjglStaticModel.tintMesh(handle, Color.RED, 1));
        assertSame(metal.positions(), tinted.positions());
        assertSame(metal.indices(), tinted.indices());
        assertEquals(200/255f, tinted.red(), .0001f);
        assertEquals(50/255f, tinted.blue(), .0001f);
        assertEquals(1, metal.red());
    }

    @Test void deliveredMaceHasReusableHeadAndExactGripMarker() throws Exception {
        var model = LwjglStaticModel.load("assets/3D/weapons/mace/gritty_mace.glb");
        assertTrue(model.meshes().stream().anyMatch(LwjglStaticModel.Mesh::tierTintable));
        assertTrue(model.meshes().stream().anyMatch(m -> !m.tierTintable()));
        assertTrue(model.meshes().stream().mapToInt(m -> m.indices().length / 3).sum() < 3000);
        var metadata = StaticModelPlacementMetadataResolver.resolve("assets/3D/weapons/mace/gritty_mace.glb");
        assertNotNull(metadata.node("FP_GRIP_PRIMARY"));
        var copper = model.withMaterial(GearMaterial.COPPER);
        assertSame(copper, model.withMaterial(GearMaterial.COPPER));
        for (int i=0; i<model.meshes().size(); i++) {
            if (!model.meshes().get(i).tierTintable()) assertSame(model.meshes().get(i), copper.meshes().get(i));
        }
    }
}
