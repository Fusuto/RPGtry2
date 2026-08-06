package org.main.core;

import org.main.battle.BattleSkill;
import org.main.engine.AssetLoader;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class LimbItem extends InventorySystem.Item {
    private final String sourceCreatureId;
    private final String sourceCreatureName;
    private final LimbSlot limbSlot;
    private final EnumMap<PlayerStat, Integer> baseStats;
    private final List<BattleSkill> skills;
    private final GearDurability condition;
    private final String iconPath;
    private final String examineText;
    private final String paperDollSourcePath;
    private final boolean paperDollDerivedIcon;
    private String firstPersonModelPath = "";
    private String firstPersonRigId = "";

    public LimbItem(
            String name,
            String sourceCreatureName,
            LimbSlot limbSlot,
            Map<PlayerStat, Integer> baseStats,
            List<BattleSkill> skills,
            GearDurability condition,
            String iconPath
    ) {
        this(name, sourceCreatureName, limbSlot, baseStats, skills, condition, iconPath, "");
    }

    public LimbItem(
            String name,
            String sourceCreatureName,
            LimbSlot limbSlot,
            Map<PlayerStat, Integer> baseStats,
            List<BattleSkill> skills,
            GearDurability condition,
            String iconPath,
            String examineText
    ) {
        this(name, sourceCreatureName, limbSlot, baseStats, skills, condition, iconPath, examineText, "");
    }

    public LimbItem(
            String name,
            String sourceCreatureName,
            LimbSlot limbSlot,
            Map<PlayerStat, Integer> baseStats,
            List<BattleSkill> skills,
            GearDurability condition,
            String iconPath,
            String examineText,
            String paperDollSourcePath
    ) {
        this(name, "", sourceCreatureName, limbSlot, baseStats, skills, condition, iconPath,
                examineText, paperDollSourcePath, false, 25);
    }

    public LimbItem(
            String name,
            String sourceCreatureId,
            String sourceCreatureName,
            LimbSlot limbSlot,
            Map<PlayerStat, Integer> baseStats,
            List<BattleSkill> skills,
            GearDurability condition,
            String iconPath,
            String examineText,
            String paperDollSourcePath
    ) {
        this(name, sourceCreatureId, sourceCreatureName, limbSlot, baseStats, skills, condition,
                iconPath, examineText, paperDollSourcePath, false, 25);
    }

    public LimbItem(
            String name,
            String sourceCreatureId,
            String sourceCreatureName,
            LimbSlot limbSlot,
            Map<PlayerStat, Integer> baseStats,
            List<BattleSkill> skills,
            GearDurability condition,
            String iconPath,
            String examineText,
            String paperDollSourcePath,
            boolean paperDollDerivedIcon,
            int baseGoldValue
    ) {
        super(name, InventorySystem.ItemType.LIMB,
                paperDollDerivedIcon
                        ? PaperDollLimbIconFactory.icon(paperDollSourcePath, limbSlot)
                        : loadIcon(iconPath),
                null, 0, GearMaterial.NONE, condition, Math.max(1, baseGoldValue));
        this.sourceCreatureId = sourceCreatureId == null ? "" : sourceCreatureId;
        this.sourceCreatureName = sourceCreatureName == null ? "" : sourceCreatureName;
        this.limbSlot = limbSlot;
        this.baseStats = new EnumMap<>(PlayerStat.class);
        this.skills = skills == null ? List.of() : List.copyOf(skills);
        this.condition = condition == null ? GearDurability.GOOD : condition;
        this.iconPath = iconPath;
        this.examineText = examineText == null ? "" : examineText;
        this.paperDollSourcePath = paperDollSourcePath == null ? "" : paperDollSourcePath;
        this.paperDollDerivedIcon = paperDollDerivedIcon;

        for (PlayerStat stat : PlayerStat.values()) {
            this.baseStats.put(stat, Math.max(0, baseStats == null ? 0 : baseStats.getOrDefault(stat, 0)));
        }
    }

    private static BufferedImage loadIcon(String iconPath) {
        return iconPath == null || iconPath.isBlank() ? null : AssetLoader.loadImage(iconPath);
    }

    public String getSourceCreatureId() {
        return sourceCreatureId;
    }

    public String getSourceCreatureName() {
        return sourceCreatureName;
    }

    public LimbSlot getLimbSlot() {
        return limbSlot;
    }

    public Map<PlayerStat, Integer> getBaseStatsView() {
        return Map.copyOf(baseStats);
    }

    public int getEffectiveStat(PlayerStat stat) {
        return (int) Math.round(baseStats.getOrDefault(stat, 0) * condition.getStatMultiplier());
    }

    public List<BattleSkill> getSkills() {
        return skills;
    }

    public GearDurability getCondition() {
        return condition;
    }

    public String getIconPath() {
        return iconPath;
    }

    public String getPaperDollSourcePath() {
        return paperDollSourcePath;
    }

    public boolean usesPaperDollDerivedIcon() {
        return paperDollDerivedIcon;
    }

    public String getFirstPersonModelPath() {
        return firstPersonModelPath;
    }

    public String getFirstPersonRigId() {
        return firstPersonRigId;
    }

    public boolean hasFirstPersonModel() {
        return !firstPersonModelPath.isBlank() && limbSlot != null && limbSlot.isArm();
    }

    public LimbItem withFirstPersonModel(String modelPath, String rigId) {
        firstPersonModelPath = modelPath == null ? "" : modelPath.trim().replace('\\', '/');
        firstPersonRigId = rigId == null ? "" : rigId.trim();
        return this;
    }

    @Override
    public String getExamineText() {
        return examineText;
    }

    public boolean isBroken() {
        return condition == GearDurability.BROKEN;
    }

    public LimbItem withCondition(GearDurability newCondition) {
        LimbItem copy = new LimbItem(
                getName(),
                sourceCreatureId,
                sourceCreatureName,
                limbSlot,
                baseStats,
                skills,
                newCondition,
                iconPath,
                examineText,
                paperDollSourcePath,
                paperDollDerivedIcon,
                getBaseGoldValue()
        ).withFirstPersonModel(firstPersonModelPath, firstPersonRigId);
        copy.withContentId(getContentId());
        return copy;
    }

    public LimbItem withSkills(List<BattleSkill> inheritedSkills) {
        LimbItem copy = new LimbItem(
                getName(),
                sourceCreatureId,
                sourceCreatureName,
                limbSlot,
                baseStats,
                inheritedSkills,
                condition,
                iconPath,
                examineText,
                paperDollSourcePath,
                paperDollDerivedIcon,
                getBaseGoldValue()
        ).withFirstPersonModel(firstPersonModelPath, firstPersonRigId);
        copy.withContentId(getContentId());
        return copy;
    }

    @Override
    public LimbItem copy() {
        return withCondition(condition);
    }
}
