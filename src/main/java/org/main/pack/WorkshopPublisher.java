package org.main.pack;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface WorkshopPublisher {
    CompletionStage<PublishResult> publish(Path validatedFolder, String existingWorkshopId);

    record PublishResult(boolean available, boolean published, String workshopId, String message) {
    }

    static WorkshopPublisher unavailable() {
        return (folder, existingId) -> CompletableFuture.completedFuture(
                new PublishResult(false, false, existingId == null ? "" : existingId,
                        "Steam Workshop publishing is unavailable in this build."));
    }
}
