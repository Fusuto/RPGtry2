package org.main.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.main.pack.ContentPackManifest;
import org.main.pack.ContentPackRegistry;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class AssetRepositoryTest {
    @TempDir
    Path temporaryFolder;

    @Test
    void repeatedImageLoadsDecodeOncePerResourceRevision() throws Exception {
        Path project = temporaryFolder.resolve("project");
        Files.createDirectories(project.resolve("assets/packs/cache_test"));
        Files.writeString(project.resolve(ContentPackManifest.MANIFEST_PATH), """
                pack.formatVersion=1
                pack.type=content
                pack.id=cache.test
                pack.namespace=cache_test
                pack.version=1.0.0
                pack.title=Cache Test
                pack.author=Test
                pack.description=Image cache test
                pack.license=Test
                contentApiVersion=1
                dependency.count=0
                override.count=0
                """);
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xFF123456);
        ImageIO.write(source, "png", project.resolve("assets/packs/cache_test/image.png").toFile());

        try (ContentPackRegistry registry = new ContentPackRegistry(
                temporaryFolder.resolve("packs"), AssetRepositoryTest.class, null, project);
             AssetRepository repository = new AssetRepository(registry)) {
            BufferedImage first = repository.image("assets/packs/cache_test/image.png");
            BufferedImage second = repository.image("assets/packs/cache_test/image.png");

            assertSame(first, second);
            assertEquals(1, repository.metrics().imageDecodes());
            assertEquals(1, repository.metrics().decodedImageCount());
        }
    }

    @Test
    void projectRefreshInvalidatesDecodedAssetIdentity() throws Exception {
        Path project = temporaryFolder.resolve("refresh-project");
        Path imagePath = project.resolve("assets/packs/cache_refresh/image.png");
        Files.createDirectories(imagePath.getParent());
        Files.writeString(project.resolve(ContentPackManifest.MANIFEST_PATH), """
                pack.formatVersion=1
                pack.type=content
                pack.id=cache.refresh
                pack.namespace=cache_refresh
                pack.version=1.0.0
                pack.title=Cache Refresh
                pack.author=Test
                pack.description=Image refresh test
                pack.license=Test
                contentApiVersion=1
                dependency.count=0
                override.count=0
                """);
        BufferedImage firstSource = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        firstSource.setRGB(0, 0, 0xFF123456);
        ImageIO.write(firstSource, "png", imagePath.toFile());

        try (ContentPackRegistry registry = new ContentPackRegistry(
                temporaryFolder.resolve("refresh-packs"), AssetRepositoryTest.class, null, project);
             AssetRepository repository = new AssetRepository(registry)) {
            BufferedImage first = repository.image("assets/packs/cache_refresh/image.png");
            BufferedImage replacement = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
            replacement.setRGB(0, 0, 0xFFABCDEF);
            ImageIO.write(replacement, "png", imagePath.toFile());
            repository.reload();
            BufferedImage second = repository.image("assets/packs/cache_refresh/image.png");

            assertNotSame(first, second);
            assertEquals(3, second.getWidth());
            assertEquals(2, repository.metrics().imageDecodes());
        }
    }
}
