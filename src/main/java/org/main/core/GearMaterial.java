package org.main.core;

import java.awt.Color;
import java.util.Locale;
import java.util.Objects;

/**
 * Stable material-ID value used by runtime items. Definitions are resolved
 * through {@link MaterialCatalog}, so the available tiers are data-driven.
 */
public final class GearMaterial implements Comparable<GearMaterial> {
    public enum MaterialFamily { NONE, METAL, WOOD, HIDE }

    public static final GearMaterial NONE = new GearMaterial("none");
    public static final GearMaterial COPPER = new GearMaterial("copper");
    public static final GearMaterial TIN = new GearMaterial("tin");
    public static final GearMaterial BRONZE = new GearMaterial("bronze");
    public static final GearMaterial SILVER = new GearMaterial("silver");
    public static final GearMaterial IRON = new GearMaterial("iron");
    public static final GearMaterial STEEL = new GearMaterial("steel");
    public static final GearMaterial OAK = new GearMaterial("oak");
    public static final GearMaterial YEW = new GearMaterial("yew");
    public static final GearMaterial IRONWOOD = new GearMaterial("ironwood");
    public static final GearMaterial LEATHER = new GearMaterial("leather");

    private final String id;

    private GearMaterial(String id) {
        this.id = MaterialDefinition.normalizeId(id);
    }

    public static GearMaterial of(String id) {
        String normalized = MaterialDefinition.normalizeId(id);
        return normalized.isBlank() || normalized.equals("none") ? NONE : new GearMaterial(normalized);
    }

    public static GearMaterial valueOf(String value) {
        GearMaterial material = of(value);
        if (!material.isResolved()) {
            throw new IllegalArgumentException("Unknown material: " + value);
        }
        return material;
    }

    public static GearMaterial resolve(String value) {
        return of(value);
    }

    public static GearMaterial[] values() {
        return MaterialCatalog.snapshot().definitions().stream()
                .map(definition -> of(definition.id()))
                .toArray(GearMaterial[]::new);
    }

    public String id() {
        return id;
    }

    /** Retained for enum-era call sites while catalogs migrate to stable IDs. */
    public String name() {
        return id.toUpperCase(Locale.ROOT);
    }

    public boolean isResolved() {
        return MaterialCatalog.snapshot().contains(id);
    }

    public MaterialDefinition definition() {
        MaterialDefinition definition = MaterialCatalog.snapshot().find(id);
        if (definition != null) {
            return definition;
        }
        return new MaterialDefinition(id, id + " (Unavailable)", MaterialFamily.NONE,
                Integer.MAX_VALUE, 0, 1.0, 0xB4B4B4, 0.0f, "", "");
    }

    public String getDisplayName() {
        return definition().displayName();
    }

    public MaterialFamily getFamily() {
        return definition().family();
    }

    public int getStatBonus() {
        return definition().statBonus();
    }

    public double getPriceMultiplier() {
        return definition().priceMultiplier();
    }

    public Color getTintColor() {
        return definition().tintColor();
    }

    public float getTintStrength() {
        return definition().tintStrength();
    }

    public String getRawResourceItemId() {
        return definition().rawResourceItemId();
    }

    public String getProcessedResourceItemId() {
        return definition().processedResourceItemId();
    }

    @Override
    public int compareTo(GearMaterial other) {
        int order = Integer.compare(definition().sortOrder(), other == null ? Integer.MAX_VALUE : other.definition().sortOrder());
        return order != 0 ? order : id.compareTo(other == null ? "" : other.id);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof GearMaterial other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
