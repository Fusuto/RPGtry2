package org.main.content;

import org.main.core.MaterialCatalog;
import org.main.core.MaterialDefinition;
import org.main.pack.ContentPackManifest;
import org.main.tools.ConstructionKitProjectService;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Runs in an isolated class loader so application path singletons match packaged startup. */
public final class FirstPersonProjectPublicationProbe {
    private static final String DISPLAY_NAME = "Published Viewmodel Test Rig";
    private static final String PROFILE_ID = "aether_user__publication_probe";
    private static final String BATTLE_DISPLAY_NAME = "Published Battle Skill";
    private static final String MATERIAL_DISPLAY_NAME = "Published Material";
    private static String changedSkillId;
    private static String changedMaterialId;

    private FirstPersonProjectPublicationProbe() {
    }

    public static void writeAndVerify(Path ignoredRoot) throws Exception {
        ConstructionKitProjectService projects = ConstructionKitProjectService.active();
        FirstPersonCombatLibrary.Content original = FirstPersonCombatLibrary.loadFresh();
        FirstPersonCombatLibrary.RigDefinition rig = original.rig();
        FirstPersonCombatLibrary.RigDefinition changed = new FirstPersonCombatLibrary.RigDefinition(
                rig.rigId(), DISPLAY_NAME, rig.modelPath(), rig.defaultLeftArmPath(), rig.defaultRightArmPath(),
                rig.leftShoulderBone(), rig.leftElbowBone(), rig.leftHandBone(),
                rig.rightShoulderBone(), rig.rightElbowBone(), rig.rightHandBone(), rig.cameraAnchorBone(),
                rig.leftVisibleMeshes(), rig.rightVisibleMeshes(),
                rig.positionX(), rig.positionY(), rig.positionZ(),
                rig.rotationX(), rig.rotationY(), rig.rotationZ(), rig.scale(),
                rig.fieldOfViewDegrees(), rig.nearPlane(), rig.crossfadeMs(), rig.fallbackBindings());

        FirstPersonCombatLibrary.Content published = FirstPersonCombatLibrary.saveProject(original.withRig(changed));
        requireDisplayName(published);
        requireDisplayName(FirstPersonCombatLibrary.loadFresh());

        FirstPersonCombatLibrary.ItemProfile profile = new FirstPersonCombatLibrary.ItemProfile(
                PROFILE_ID, rig.rigId(), FirstPersonCombatLibrary.WieldHand.RIGHT, "",
                FirstPersonCombatLibrary.ItemProfile.socketDefaults(), 0, 0, 0,
                "", "", FirstPersonCombatLibrary.ArmCoverage.OVERLAY,
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY, "",
                FirstPersonCombatLibrary.AnimationCompositionMode.AUTO, Map.of());
        FirstPersonCombatLibrary.saveProject(
                FirstPersonCombatLibrary.loadFresh().withItemProfile(profile));
        if (!FirstPersonCombatLibrary.loadFresh().itemProfiles().containsKey(PROFILE_ID)) {
            throw new AssertionError("Published first-person item profile was not visible after reopening.");
        }

        FirstPersonCombatLibrary.saveProject(
                FirstPersonCombatLibrary.loadFresh().withoutItemProfile(PROFILE_ID));
        if (FirstPersonCombatLibrary.loadFresh().itemProfiles().containsKey(PROFILE_ID)) {
            throw new AssertionError("Removed first-person item profile returned after reopening.");
        }

        Path catalog = projects.projectRoot().resolve(
                "assets/packs/aether_user/editor/content/first_person_rig.properties");
        if (!Files.isRegularFile(catalog)) {
            throw new AssertionError("First-person project catalog was not written to " + catalog);
        }
        requireCoreOverride(projects.projectRoot(), rig.rigId());
        publishAndVerifyBattleCatalog();
        publishAndVerifyMaterialCatalog();
    }

    public static void verifyPersisted(Path ignoredRoot) throws Exception {
        ConstructionKitProjectService.active();
        FirstPersonCombatLibrary.Content reopened = FirstPersonCombatLibrary.loadFresh();
        requireDisplayName(reopened);
        if (reopened.itemProfiles().containsKey(PROFILE_ID)) {
            throw new AssertionError("Removed first-person item profile returned after restart.");
        }
        requireBattleCatalog();
        requireMaterialCatalog();
    }

    private static void requireDisplayName(FirstPersonCombatLibrary.Content content) {
        if (!DISPLAY_NAME.equals(content.rig().displayName())) {
            throw new AssertionError("Expected persisted rig name '" + DISPLAY_NAME
                    + "' but loaded '" + content.rig().displayName() + "'.");
        }
    }

    private static void requireCoreOverride(Path project, String rigId) throws Exception {
        Properties manifest = new Properties();
        try (InputStream input = Files.newInputStream(project.resolve(ContentPackManifest.MANIFEST_PATH))) {
            manifest.load(input);
        }
        int dependencies = Integer.parseInt(manifest.getProperty("dependency.count", "0"));
        boolean coreDependency = false;
        for (int index = 0; index < dependencies; index++) {
            coreDependency |= "aether.core".equals(manifest.getProperty("dependency." + index + ".id"));
        }
        int overrides = Integer.parseInt(manifest.getProperty("override.count", "0"));
        boolean rigOverride = false;
        for (int index = 0; index < overrides; index++) {
            String prefix = "override." + index + ".";
            rigOverride |= "aether.core".equals(manifest.getProperty(prefix + "targetPack"))
                    && "first_person_rig".equals(manifest.getProperty(prefix + "type"))
                    && rigId.equals(manifest.getProperty(prefix + "id"));
        }
        if (!coreDependency || !rigOverride) {
            throw new AssertionError("Published project manifest is missing the required core rig override.");
        }
    }

    private static void publishAndVerifyBattleCatalog() throws Exception {
        BattleContentCatalog.Snapshot original = BattleContentCatalog.reload();
        SkillDefinition source = original.skills().values().iterator().next();
        changedSkillId = source.id();
        SkillDefinition changed = new SkillDefinition(
                source.id(), BATTLE_DISPLAY_NAME, source.description(), source.targetShape(),
                source.targetTeam(), source.targetingMode(), source.useSoundPath(),
                source.presentationStyle(), source.cooldownSeconds(), source.consumesAutoAction(),
                source.effects(), source.element());
        LinkedHashMap<String, SkillDefinition> skills = new LinkedHashMap<>(original.skills());
        skills.put(changed.id(), changed);
        BattleContentCatalog.save(new BattleContentCatalog.Snapshot(
                original.schemaVersion(), skills, original.statuses(), original.defaultPlayerSkillIds(),
                original.universalPlayerSkillIds(), original.debugPlayerSkillIds()));
        requireBattleCatalog();
    }

    private static void requireBattleCatalog() throws Exception {
        BattleContentCatalog.Snapshot published = BattleContentCatalog.reload();
        String skillId = changedSkillId == null || changedSkillId.isBlank()
                ? published.skills().values().stream()
                .filter(skill -> BATTLE_DISPLAY_NAME.equals(skill.displayName()))
                .map(SkillDefinition::id).findFirst().orElse("")
                : changedSkillId;
        SkillDefinition skill = published.skills().get(skillId);
        if (skill == null || !BATTLE_DISPLAY_NAME.equals(skill.displayName())) {
            throw new AssertionError("Published battle catalog was not visible after reopening.");
        }
    }

    private static void publishAndVerifyMaterialCatalog() throws Exception {
        ArrayList<MaterialDefinition> materials = new ArrayList<>(MaterialCatalog.refresh().definitions());
        MaterialDefinition source = materials.stream()
                .filter(material -> !material.id().equals("none"))
                .findFirst().orElseThrow();
        changedMaterialId = source.id();
        MaterialDefinition changed = new MaterialDefinition(
                source.id(), MATERIAL_DISPLAY_NAME, source.family(), source.sortOrder(), source.statBonus(),
                source.priceMultiplier(), source.tintRgb(), source.tintStrength(), source.rawResourceItemId(),
                source.processedResourceItemId(), source.lanternFuelCapacitySeconds(),
                source.lanternBurnSecondsPerLog());
        materials.replaceAll(material -> material.id().equals(source.id()) ? changed : material);
        MaterialCatalog.save(materials);
        requireMaterialCatalog();
    }

    private static void requireMaterialCatalog() {
        MaterialDefinition material = changedMaterialId == null || changedMaterialId.isBlank()
                ? MaterialCatalog.refresh().definitions().stream()
                .filter(value -> MATERIAL_DISPLAY_NAME.equals(value.displayName()))
                .findFirst().orElse(null)
                : MaterialCatalog.refresh().find(changedMaterialId);
        if (material == null || !MATERIAL_DISPLAY_NAME.equals(material.displayName())) {
            throw new AssertionError("Published material catalog was not visible after reopening.");
        }
    }
}
