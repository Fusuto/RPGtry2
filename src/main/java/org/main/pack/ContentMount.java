package org.main.pack;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface ContentMount extends AutoCloseable {
    ContentPackManifest manifest();

    String revision();

    /**
     * Stable content fingerprint used by save-file pack locks.
     */
    default String contentDigest() {
        return revision();
    }

    /**
     * Refreshes directory-backed revision metadata without replacing the mount.
     */
    default void refresh() throws IOException {
    }

    Origin origin();

    boolean readOnly();

    List<String> list(String prefix, boolean recursive) throws IOException;

    InputStream open(String logicalPath) throws IOException;

    default boolean contains(String logicalPath) throws IOException {
        try (InputStream input = open(logicalPath)) {
            return input != null;
        }
    }

    @Override
    default void close() throws IOException {
    }

    enum Origin {
        BUNDLED,
        DEVELOPMENT,
        PROJECT,
        INSTALLED,
        WORKSHOP
    }
}
