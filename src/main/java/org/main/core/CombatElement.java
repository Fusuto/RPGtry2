package org.main.core;

import java.awt.Color;

/**
 * Element carried by damaging magic skills and elemental casting weapons.
 */
public enum CombatElement {
    NEUTRAL("Neutral", new Color(220, 220, 220)),
    FIRE("Fire", new Color(244, 92, 54)),
    FROST("Frost", new Color(92, 190, 244)),
    STORM("Storm", new Color(202, 154, 255)),
    EARTH("Earth", new Color(150, 117, 69));

    private final String displayName;
    private final Color color;

    CombatElement(String displayName, Color color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Color getColor() {
        return color;
    }

    public boolean isElemental() {
        return this != NEUTRAL;
    }
}
