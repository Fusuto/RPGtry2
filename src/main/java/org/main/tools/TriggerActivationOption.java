package org.main.tools;

import org.main.content.MapDesignLibrary;

record TriggerActivationOption(String label, MapDesignLibrary.TriggerFireMode fireMode) {
    @Override
    public String toString() {
        return label;
    }
}

