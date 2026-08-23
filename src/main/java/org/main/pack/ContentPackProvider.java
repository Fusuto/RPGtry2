package org.main.pack;

import java.io.IOException;
import java.util.List;

public interface ContentPackProvider {
    String providerId();

    List<ContentMount> discover() throws IOException;

    default List<String> diagnostics() {
        return List.of();
    }

    default void refresh() throws IOException {
    }
}
