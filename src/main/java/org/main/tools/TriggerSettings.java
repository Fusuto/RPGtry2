package org.main.tools;

import org.main.content.MapDesignLibrary;

record TriggerSettings(
        String id,
        MapDesignLibrary.TriggerFireMode fireMode,
        boolean oneShot,
        String requiredQuestId,
        String requiredQuestProgress
) {
}

