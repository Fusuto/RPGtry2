package org.main.core;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Fuel compatibility, refuelling, activation, and countdown rules for Pocket lanterns. */
public final class LanternSystem {
    private LanternSystem() {
    }

    public static List<MaterialDefinition> compatibleWoodMaterials(String metalMaterialId) {
        return compatibleWoodMaterials(metalMaterialId, MaterialCatalog.snapshot().definitions());
    }

    private static List<MaterialDefinition> compatibleWoodMaterials(
            String metalMaterialId, List<MaterialDefinition> definitions) {
        List<MaterialDefinition> metals = family(GearMaterial.MaterialFamily.METAL, definitions);
        List<MaterialDefinition> woods = family(GearMaterial.MaterialFamily.WOOD, definitions);
        String normalized = MaterialDefinition.normalizeId(metalMaterialId);
        int metalIndex = -1;
        for (int index = 0; index < metals.size(); index++) {
            if (metals.get(index).id().equals(normalized)) {
                metalIndex = index;
                break;
            }
        }
        if (metalIndex < 0 || woods.isEmpty() || metals.isEmpty()) {
            return List.of();
        }
        int allowed = (int) Math.ceil((metalIndex + 1.0) * woods.size() / metals.size());
        return List.copyOf(woods.subList(0, Math.max(1, Math.min(allowed, woods.size()))));
    }

    public static boolean isCompatibleFuel(InventorySystem.Item lantern, InventorySystem.Item fuel) {
        if (!isLantern(lantern) || fuel == null || !fuel.isStackable() || fuel.getContentId().isBlank()) {
            return false;
        }
        return compatibleWoodMaterials(lantern.getMaterial().id()).stream()
                .anyMatch(wood -> !wood.rawResourceItemId().isBlank()
                        && wood.rawResourceItemId().equalsIgnoreCase(fuel.getContentId()));
    }

    public static MaterialDefinition fuelMaterial(InventorySystem.Item lantern, InventorySystem.Item fuel) {
        if (!isCompatibleFuel(lantern, fuel)) {
            return null;
        }
        return compatibleWoodMaterials(lantern.getMaterial().id()).stream()
                .filter(wood -> wood.rawResourceItemId().equalsIgnoreCase(fuel.getContentId()))
                .findFirst().orElse(null);
    }

    public static RefuelResult refuel(
            InventorySystem.Inventory inventory,
            int fuelInventoryIndex,
            InventorySystem.Item lantern
    ) {
        if (inventory == null || lantern == null) {
            return RefuelResult.failure("No lantern is available.");
        }
        InventorySystem.Item fuel = inventory.getItem(fuelInventoryIndex);
        if (!isCompatibleFuel(lantern, fuel)) {
            return RefuelResult.failure("That log is not compatible with this lantern.");
        }
        long capacity = lantern.getLanternCapacityMillis();
        if (capacity <= 0L) {
            return RefuelResult.failure("This lantern's metal has no fuel capacity.");
        }
        if (lantern.getLanternFuelMillis() >= capacity) {
            return RefuelResult.failure("The lantern is already full.");
        }
        MaterialDefinition wood = fuelMaterial(lantern, fuel);
        long contribution = wood == null ? 0L : wood.lanternBurnSecondsPerLog() * 1_000L;
        if (contribution <= 0L) {
            return RefuelResult.failure("That wood has no authored burn time.");
        }
        long before = lantern.getLanternFuelMillis();
        long accepted = Math.min(contribution, capacity - before);
        long overflow = contribution - accepted;
        if (!inventory.removeOneFromInventorySlot(fuelInventoryIndex)) {
            return RefuelResult.failure("The log could not be consumed.");
        }
        lantern.setLanternFuelMillis(before + accepted);
        String message = "Added " + formatDuration(accepted) + " of fuel to " + lantern.getName() + ".";
        if (overflow > 0L) {
            message += " " + formatDuration(overflow) + " could not fit and was discarded.";
        }
        return new RefuelResult(true, message, accepted, overflow);
    }

    public static InventorySystem.Item activeLantern(InventorySystem.Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventorySystem.Item item = inventory.getEquippedItem(InventorySystem.EquipmentSlot.POCKET);
        return isLantern(item) && item.getLanternFuelMillis() > 0L ? item : null;
    }

    public static boolean isLantern(InventorySystem.Item item) {
        return item != null
                && item.getItemType() == InventorySystem.ItemType.UTILITY
                && item.getLanternDefinition().enabled();
    }

    public static boolean updateFuel(GameState gameState, int deltaMs) {
        if (gameState == null || deltaMs <= 0 || !gameState.isActiveExploration()) {
            return false;
        }
        InventorySystem.Item lantern = activeLantern(gameState.getInventory());
        if (lantern == null) {
            return false;
        }
        long before = lantern.getLanternFuelMillis();
        lantern.setLanternFuelMillis(before - Math.max(0, deltaMs));
        if (before > 0L && lantern.getLanternFuelMillis() == 0L) {
            gameState.getWorldMessageLog().post(
                    WorldMessageLog.Category.WARNING,
                    lantern.getName() + " runs out of fuel and goes dark.");
        }
        return before != lantern.getLanternFuelMillis();
    }

    public static String fuelTooltip(InventorySystem.Item lantern) {
        if (!isLantern(lantern)) {
            return "";
        }
        return "Fuel " + formatClock(lantern.getLanternFuelMillis())
                + " / " + formatClock(lantern.getLanternCapacityMillis());
    }

    public static String compatibilitySummary(MaterialDefinition material) {
        if (material == null) {
            return "";
        }
        List<MaterialDefinition> previewDefinitions = new java.util.ArrayList<>(MaterialCatalog.snapshot().definitions());
        previewDefinitions.removeIf(existing -> existing.id().equals(material.id()));
        previewDefinitions.add(material);
        if (material.family() == GearMaterial.MaterialFamily.METAL) {
            List<MaterialDefinition> woods = compatibleWoodMaterials(material.id(), previewDefinitions);
            return woods.isEmpty() ? "No compatible Wood tiers"
                    : "Accepts: " + woods.stream().map(MaterialDefinition::displayName).reduce((a, b) -> a + ", " + b).orElse("");
        }
        if (material.family() == GearMaterial.MaterialFamily.WOOD) {
            List<String> metals = family(GearMaterial.MaterialFamily.METAL, previewDefinitions).stream()
                    .filter(metal -> compatibleWoodMaterials(metal.id(), previewDefinitions).stream()
                            .anyMatch(wood -> wood.id().equals(material.id())))
                    .map(MaterialDefinition::displayName).toList();
            return metals.isEmpty() ? "Accepted by no Metal tiers"
                    : "Fuels: " + String.join(", ", metals);
        }
        return "Lantern fuel is available only to Metal and Wood families";
    }

    public static String formatClock(long milliseconds) {
        long totalSeconds = Math.max(0L, milliseconds) / 1_000L;
        return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

    private static String formatDuration(long milliseconds) {
        long seconds = Math.max(0L, milliseconds) / 1_000L;
        return seconds >= 60L ? (seconds / 60L) + "m " + (seconds % 60L) + "s" : seconds + "s";
    }

    private static List<MaterialDefinition> family(GearMaterial.MaterialFamily family) {
        return family(family, MaterialCatalog.snapshot().definitions());
    }

    private static List<MaterialDefinition> family(
            GearMaterial.MaterialFamily family, List<MaterialDefinition> definitions) {
        return definitions.stream()
                .filter(definition -> definition.family() == family)
                .sorted(Comparator.comparingInt(MaterialDefinition::sortOrder)
                        .thenComparing(MaterialDefinition::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public record RefuelResult(boolean success, String message, long acceptedMillis, long overflowMillis) {
        private static RefuelResult failure(String message) {
            return new RefuelResult(false, message, 0L, 0L);
        }
    }
}
