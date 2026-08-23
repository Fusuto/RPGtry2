package org.main.engine;

import org.main.pack.ContentPackRegistry;
import org.main.pack.ContentResource;
import org.main.pack.PackPaths;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared logical asset index and decoded-image cache.
 */
public final class AssetRepository implements AutoCloseable {
    private final ContentPackRegistry registry;
    private final ConcurrentHashMap<AssetKey, BufferedImage> images = new ConcurrentHashMap<>();
    private final AtomicLong fileOpens = new AtomicLong();
    private final AtomicLong imageDecodes = new AtomicLong();

    public AssetRepository(ContentPackRegistry registry) {
        this.registry = registry;
    }

    public static AssetRepository shared() {
        return SharedHolder.INSTANCE;
    }

    public ContentPackRegistry registry() {
        return registry;
    }

    public long revision() {
        return registry.snapshot().revision();
    }

    public synchronized void reload() throws IOException {
        registry.reload();
        images.clear();
    }

    public List<ContentResource> list(String prefix, boolean recursive) throws IOException {
        return registry.list(prefix, recursive);
    }

    public Optional<ContentResource> describe(String logicalPath) throws IOException {
        Path external = externalPath(logicalPath);
        if (external != null && Files.isRegularFile(external)) {
            return Optional.of(new ContentResource(external.toString(), "aether.user", fingerprint(external),
                    org.main.pack.ContentMount.Origin.PROJECT, false));
        }
        return registry.resolve(normalizeAlias(logicalPath)).map(ContentPackRegistry.ResolvedResource::descriptor);
    }

    public InputStream open(String assetPath) throws IOException {
        Path external = externalPath(assetPath);
        if (external != null && Files.isRegularFile(external)) {
            fileOpens.incrementAndGet();
            return new BufferedInputStream(Files.newInputStream(external));
        }
        Optional<ContentPackRegistry.ResolvedResource> resource = registry.resolve(normalizeAlias(assetPath));
        if (resource.isEmpty()) {
            return null;
        }
        fileOpens.incrementAndGet();
        InputStream input = resource.get().open();
        return input == null ? null : new BufferedInputStream(input);
    }

    public BufferedImage image(String assetPath) throws IOException {
        Path external = externalPath(assetPath);
        if (external != null && Files.isRegularFile(external)) {
            AssetKey key = new AssetKey(external.toString(), "aether.user", fingerprint(external));
            return cachedImage(key, external);
        }

        Optional<ContentPackRegistry.ResolvedResource> resolved = registry.resolve(normalizeAlias(assetPath));
        if (resolved.isEmpty()) {
            return null;
        }
        ContentResource descriptor = resolved.get().descriptor();
        AssetKey key = new AssetKey(descriptor.logicalPath(), descriptor.packId(), descriptor.revision());
        BufferedImage cached = images.get(key);
        if (cached != null) {
            return cached;
        }
        try (InputStream input = resolved.get().open()) {
            if (input == null) {
                return null;
            }
            fileOpens.incrementAndGet();
            BufferedImage decoded = ImageIO.read(input);
            if (decoded == null) {
                return null;
            }
            imageDecodes.incrementAndGet();
            BufferedImage previous = images.putIfAbsent(key, decoded);
            return previous == null ? decoded : previous;
        }
    }

    public void invalidate(String assetPath) {
        String normalized = assetPath == null ? "" : assetPath.replace('\\', '/');
        images.keySet().removeIf(key -> key.logicalPath().equals(normalized)
                || key.logicalPath().endsWith("/" + normalized));
    }

    public void clearDecodedImages() {
        images.clear();
    }

    public Metrics metrics() {
        return new Metrics(fileOpens.get(), imageDecodes.get(), images.size(), revision());
    }

    @Override
    public void close() throws IOException {
        images.clear();
        registry.close();
    }

    private BufferedImage cachedImage(AssetKey key, Path path) throws IOException {
        BufferedImage cached = images.get(key);
        if (cached != null) {
            return cached;
        }
        fileOpens.incrementAndGet();
        BufferedImage decoded = ImageIO.read(path.toFile());
        if (decoded == null) {
            return null;
        }
        imageDecodes.incrementAndGet();
        BufferedImage previous = images.putIfAbsent(key, decoded);
        return previous == null ? decoded : previous;
    }

    private static Path externalPath(String assetPath) {
        if (assetPath == null || assetPath.isBlank()) {
            return null;
        }
        String normalized = assetPath.replace('\\', '/');
        Path direct;
        try {
            direct = Path.of(normalized);
        } catch (RuntimeException error) {
            return null;
        }
        if (direct.isAbsolute()) {
            return allowedExternalPath(direct);
        }
        if (normalized.startsWith("data/")) {
            return allowedExternalPath(ApplicationPaths.resolveApplicationPath(normalized));
        }
        if (normalized.startsWith("assets/")) {
            Path resources = ApplicationPaths.developmentResourcesFolder();
            if (resources != null) {
                try {
                    Path candidate = PackPaths.resolve(resources, normalized);
                    if (Files.exists(candidate)) {
                        return candidate;
                    }
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    private static Path allowedExternalPath(Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        Path[] roots = {
                ApplicationPaths.dataFolder(),
                ApplicationPaths.developmentResourcesFolder(),
                ApplicationPaths.activeProjectFolder()
        };
        for (Path root : roots) {
            if (root == null) continue;
            Path normalizedRoot = root.toAbsolutePath().normalize();
            if (!normalized.startsWith(normalizedRoot)) continue;
            try {
                if (Files.exists(normalized) && Files.exists(normalizedRoot)
                        && !normalized.toRealPath().startsWith(normalizedRoot.toRealPath())) {
                    return null;
                }
            } catch (IOException error) {
                return null;
            }
            return normalized;
        }
        return null;
    }

    private static String normalizeAlias(String assetPath) throws IOException {
        String normalized = assetPath == null ? "" : assetPath.replace('\\', '/');
        return PackPaths.normalize(normalized);
    }

    private static String fingerprint(Path path) {
        try {
            return Files.size(path) + ":" + Files.getLastModifiedTime(path).toMillis();
        } catch (IOException error) {
            return "missing";
        }
    }

    public record AssetKey(String logicalPath, String packId, String revision) {
    }

    public record Metrics(long fileOpens, long imageDecodes, int decodedImageCount, long packRevision) {
    }

    private static final class SharedHolder {
        private static final AssetRepository INSTANCE = create();

        private static AssetRepository create() {
            try {
                return new AssetRepository(new ContentPackRegistry(
                        ApplicationPaths.contentPacksFolder(),
                        AssetRepository.class,
                        ApplicationPaths.developmentResourcesFolder(),
                        ApplicationPaths.activeProjectFolder()
                ));
            } catch (IOException error) {
                throw new ExceptionInInitializerError(error);
            }
        }
    }
}
