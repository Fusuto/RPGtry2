package org.main.pack;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class BundledContentMount implements ContentMount {
    private final ContentMount delegate;

    public BundledContentMount(Class<?> anchor) throws IOException {
        try {
            Path location = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath().normalize();
            if (Files.isDirectory(location)) {
                delegate = new DirectoryContentMount(location, ContentPackManifest.core(), Origin.BUNDLED, true);
            } else {
                delegate = new ZipContentMount(location, Origin.BUNDLED, ContentPackManifest.core());
            }
        } catch (URISyntaxException | RuntimeException error) {
            throw new IOException("Unable to locate bundled Aether content.", error);
        }
    }

    @Override
    public ContentPackManifest manifest() {
        return delegate.manifest();
    }

    @Override
    public String revision() {
        return delegate.revision();
    }

    @Override
    public String contentDigest() {
        return delegate.contentDigest();
    }

    @Override
    public void refresh() throws IOException {
        delegate.refresh();
    }

    @Override
    public Origin origin() {
        return Origin.BUNDLED;
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public List<String> list(String prefix, boolean recursive) throws IOException {
        return delegate.list(prefix, recursive);
    }

    @Override
    public InputStream open(String logicalPath) throws IOException {
        return delegate.open(logicalPath);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
