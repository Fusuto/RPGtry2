package org.main.pack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Supplies subscribed Workshop item folders without exposing platform-specific paths to content loading.
 */
public interface WorkshopPackLocationProvider {
    boolean available();

    List<WorkshopItemLocation> subscribedItems() throws IOException;

    default void refresh() throws IOException {
    }

    record WorkshopItemLocation(String itemId, Path folder) {
        public WorkshopItemLocation {
            itemId = itemId == null ? "" : itemId.trim();
            folder = folder == null ? null : folder.toAbsolutePath().normalize();
        }
    }

    static WorkshopPackLocationProvider unavailable() {
        return new WorkshopPackLocationProvider() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public List<WorkshopItemLocation> subscribedItems() {
                return List.of();
            }
        };
    }

    static WorkshopPackLocationProvider fixed(List<WorkshopItemLocation> items) {
        List<WorkshopItemLocation> snapshot = items == null ? List.of() : List.copyOf(items);
        return new WorkshopPackLocationProvider() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public List<WorkshopItemLocation> subscribedItems() {
                return snapshot;
            }
        };
    }
}
