package org.main.tools;

import org.main.content.*;
import org.main.content.MapDesignLibrary.*;
import org.main.core.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** First weapon-authoring command: source-resource projects, with explicit template inheritance. */
public final class ConstructionKitWeaponService {
    public record Result(CustomItem item, List<ValidationIssue> issues, boolean saved) {}

    public Result create(Path root, String templateId, String id, String name, WeaponType type,
                         GearMaterial material, String modelPath, String iconPath, boolean dryRun) throws IOException {
        if (Files.exists(root.resolve("aether-pack.properties"))) {
            throw new IOException("create-weapon currently supports source resource roots only");
        }
        if (!id.matches("[A-Za-z][A-Za-z0-9_]*") || type == WeaponType.NONE) {
            throw new IllegalArgumentException("A stable item ID and a weapon type are required");
        }
        requireAsset(root, modelPath);
        requireAsset(root, iconPath);
        MapDesign map = MapDesignLibrary.createBlank(3, 3, null, null);
        MapDesignLibrary.mergeAuthoredContent(map, MapDesignLibrary.loadSharedContent());
        List<ValidationIssue> baseline = MapDesignLibrary.validate(map);
        if (map.customItems().stream().anyMatch(i -> i.itemId().equalsIgnoreCase(id))) {
            throw new IllegalArgumentException("Item already exists: " + id);
        }
        CustomItem template = map.customItems().stream().filter(i -> i.itemId().equals(templateId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown template: " + templateId));
        if (template.itemType() != InventorySystem.ItemType.WEAPON) {
            throw new IllegalArgumentException("Template must be a weapon");
        }
        CustomItem item = new CustomItem(id, name, InventorySystem.ItemType.WEAPON, iconPath,
                "", template.useSoundPath(), type, false, material, 0, template.baseGoldValue(),
                "A worn " + material.getDisplayName().toLowerCase(Locale.ROOT) + " "
                        + type.name().toLowerCase(Locale.ROOT) + ".",
                template.statBonusTarget(), false, template.smithingRecipeEnabled(),
                template.smithingRequiredBars(), template.smithingRequiredLevel(), 0, 0,
                modelPath, EquipmentViewModelProfile.defaults(), "", ItemModelIconProfile.defaults(),
                template.equipmentSkill(), LanternDefinition.none(), WeaponStatOverrides.inherited(),
                CombatElement.NEUTRAL, 0);
        map.customItems().add(item);
        List<ValidationIssue> issues = MapDesignLibrary.validate(map);
        if (!ConstructionKitMapService.noNewErrors(baseline, issues)) {
            throw new IOException("Weapon introduces validation errors: " + issues.stream()
                    .filter(i -> i.severity() == ValidationSeverity.ERROR && !baseline.contains(i)).toList());
        }
        FirstPersonCombatLibrary.Content rigs = FirstPersonCombatLibrary.load();
        var initial = new FirstPersonCombatLibrary.ItemProfile(id, FirstPersonCombatLibrary.WieldHand.RIGHT,
                "", FirstPersonCombatLibrary.ItemProfile.socketDefaults(), 0, 0, 0, "", "",
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY, FirstPersonCombatLibrary.ArmCoverage.OVERLAY, Map.of());
        var proposal = EquipmentAutoPlacementService.proposeForAttachment(modelPath,
                InventorySystem.ItemType.WEAPON, type, false, false, rigs, initial);
        var profile = new FirstPersonCombatLibrary.ItemProfile(id, FirstPersonCombatLibrary.WieldHand.RIGHT,
                "", proposal.socket(), 0, 0, 0, "", "", FirstPersonCombatLibrary.ArmCoverage.OVERLAY,
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY, Map.of());
        if (!dryRun) {
            Path items = root.resolve("assets/editor/content/item.properties");
            Path firstPerson = root.resolve("assets/editor/content/first_person_rig.properties");
            byte[] originalItems = Files.readAllBytes(items);
            byte[] originalRig = Files.readAllBytes(firstPerson);
            Path staged = Files.createTempFile(items.getParent(), ".agent-items-", ".properties");
            try {
                MapDesignLibrary.saveItemCatalog(map.customItems(), staged);
                FirstPersonCombatLibrary.save(firstPerson, rigs.withItemProfile(profile));
                Files.move(staged, items, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException | RuntimeException error) {
                Files.write(items, originalItems);
                Files.write(firstPerson, originalRig);
                throw error;
            } finally {
                Files.deleteIfExists(staged);
            }
        }
        return new Result(item, issues, !dryRun);
    }

    private static void requireAsset(Path root, String asset) throws IOException {
        Path path = root.resolve(asset).normalize();
        if (!asset.startsWith("assets/") || !path.startsWith(root) || !Files.isRegularFile(path)
                || !path.toRealPath().startsWith(root.toRealPath())) throw new IOException("Missing or invalid asset: " + asset);
    }
}
