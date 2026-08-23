package org.main.pack;

public record ContentResource(
        String logicalPath,
        String packId,
        String revision,
        ContentMount.Origin origin,
        boolean readOnly
) {
}
