package org.main.tools;

import org.main.content.ContentRepository;
import org.main.content.MapDesignLibrary;
import org.main.content.WorldManifestLibrary;
import org.main.engine.AssetLoader;

import java.nio.file.Path;

/**
 * Headless updater gate for required entrypoints and packaged core content.
 */
public final class ReleaseSelfCheck {
    private ReleaseSelfCheck() {
    }

    public static void main(String[] args) throws Exception {
        if (AssetLoader.listAssetFiles("assets").isEmpty()) {
            throw new IllegalStateException("Packaged asset inventory is empty.");
        }
        ContentRepository.shared().snapshot();
        Path manifestPath = Path.of("assets/editor/worlds/overworld/world.properties");
        var manifest = WorldManifestLibrary.load(manifestPath);
        if (manifest.chunks().size() != 17) {
            throw new IllegalStateException("Expected 17 overworld chunks, found " + manifest.chunks().size() + ".");
        }
        for (String chunk : manifest.chunks().values()) {
            MapDesignLibrary.loadGeometryOnly(WorldManifestLibrary.resolveChunkPath(manifestPath, chunk));
        }
        System.out.println("Aether release self-check passed.");
    }
}
