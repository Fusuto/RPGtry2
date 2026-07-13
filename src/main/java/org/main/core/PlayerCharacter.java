package org.main.core;

import org.main.battle.BattleSkill;
import org.main.content.PlayerRegionLibrary;
import org.main.content.SkillLibrary;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerCharacter {
    private static final int MIN_MAX_HP = 1;
    private static final int MIN_CURRENT_HP = 0;
    private static final int DEFAULT_VITALITY = 10;

    private final String name;
    private final HashMap<CharacterSkill, Integer> skills;
    private final HashMap<CharacterSkill, Integer> skillExperience = new HashMap<>();
    private final EnumMap<PlayerStat, Integer> stats;
    private final EnumMap<LimbSlot, LimbItem> equippedLimbs = new EnumMap<>(LimbSlot.class);
    private final List<BattleSkill> battleSkills;
    private final PlayerRegionLibrary playerRegion;
    private int level = 1;
    private int classExperience = 0;
    private int availableStatPoints = 0;
    private int maxHp;
    private int currHp;
    private boolean debugSkillsLoaded = false;
    private final InventorySystem.Inventory inventory;
    private final String portraitPath;

    public PlayerCharacter(String name, int maxHp, int currHp) {
        this(name, maxHp, currHp, new InventorySystem.Inventory(), createDefaultSkills(), null);
    }

    public PlayerCharacter(
            String name,
            int maxHp,
            int currHp,
            InventorySystem.Inventory inventory,
            HashMap<CharacterSkill, Integer> skills
    ) {
        this(name, maxHp, currHp, inventory, skills, null);
    }

    public PlayerCharacter(
            String name,
            int maxHp,
            int currHp,
            InventorySystem.Inventory inventory,
            HashMap<CharacterSkill, Integer> skills,
            String portraitPath
    ) {
        this(name, maxHp, currHp, inventory, skills, portraitPath, null, createDefaultStats(), SkillLibrary.createDefaultPlayerSkills());
    }

    public PlayerCharacter(
            String name,
            int maxHp,
            int currHp,
            InventorySystem.Inventory inventory,
            HashMap<CharacterSkill, Integer> skills,
            String portraitPath,
            PlayerRegionLibrary playerRegion,
            Map<PlayerStat, Integer> stats,
            List<BattleSkill> battleSkills
    ) {
        this.name = name == null || name.isBlank() ? "Player" : name.trim();
        this.maxHp = Math.max(1, maxHp);
        this.currHp = clampHp(currHp);
        this.inventory = inventory == null ? new InventorySystem.Inventory() : inventory;
        this.skills = skills == null ? createDefaultSkills() : skills;
        this.portraitPath = portraitPath;
        this.playerRegion = playerRegion;
        this.stats = new EnumMap<>(PlayerStat.class);

        for (PlayerStat stat : PlayerStat.values()) {
            int defaultValue = stat == PlayerStat.VITALITY ? DEFAULT_VITALITY : 1;
            this.stats.put(stat, Math.max(0, stats == null ? defaultValue : stats.getOrDefault(stat, defaultValue)));
        }

        this.battleSkills = new ArrayList<>();

        if (battleSkills != null) {
            this.battleSkills.addAll(battleSkills);
        }

        refreshBattleSkillsFromLimbs();
        recalculateMaxHp(false);
    }

    public static HashMap<CharacterSkill, Integer> createDefaultSkills() {
        HashMap<CharacterSkill, Integer> defaultSkills = new HashMap<>();

        for (CharacterSkill skill : CharacterSkill.values()) {
            defaultSkills.put(skill, 1);
        }

        return defaultSkills;
    }

    public static EnumMap<PlayerStat, Integer> createDefaultStats() {
        EnumMap<PlayerStat, Integer> defaultStats = new EnumMap<>(PlayerStat.class);

        for (PlayerStat stat : PlayerStat.values()) {
            defaultStats.put(stat, stat == PlayerStat.VITALITY ? DEFAULT_VITALITY : 1);
        }

        return defaultStats;
    }

    public String getName() {
        return name;
    }

    public HashMap<CharacterSkill, Integer> getSkills() {
        return skills;
    }

    public PlayerRegionLibrary getPlayerRegion() {
        return playerRegion;
    }

    public int getLevel() {
        return level;
    }

    public int getAvailableStatPoints() {
        return availableStatPoints;
    }

    public int getClassExperience() {
        return classExperience;
    }

    public int getClassExperienceRequiredForNextLevel() {
        return Math.max(1, level * 100);
    }

    public int addClassExperience(int amount) {
        if (amount <= 0) {
            return 0;
        }

        classExperience += amount;
        int levelsGained = 0;

        while (classExperience >= getClassExperienceRequiredForNextLevel()) {
            classExperience -= getClassExperienceRequiredForNextLevel();
            levelUp(Map.of());
            levelsGained++;
        }

        return levelsGained;
    }

    public void restoreProgress(int level, int classExperience, int availableStatPoints) {
        this.level = Math.max(1, level);
        this.classExperience = Math.max(0, classExperience);
        this.availableStatPoints = Math.max(0, availableStatPoints);
    }

    public int getStat(PlayerStat stat) {
        int total = stats.getOrDefault(stat, 0);

        for (LimbItem limb : equippedLimbs.values()) {
            if (limb != null) {
                total += limb.getEffectiveStat(stat);
            }
        }

        return total;
    }

    public int getEquipmentStatBonus(PlayerStat stat) {
        if (stat == null) {
            return 0;
        }

        int total = 0;

        for (Map.Entry<InventorySystem.EquipmentSlot, InventorySystem.Item> entry : inventory.getEquippedItemsView().entrySet()) {
            InventorySystem.Item item = entry.getValue();
            if ((entry.getKey() == InventorySystem.EquipmentSlot.RING_LEFT
                    || entry.getKey() == InventorySystem.EquipmentSlot.RING_RIGHT)
                    && item != null
                    && item.getStatBonusTarget() == stat
                    && canUseEquipment(item, entry.getKey())) {
                total += item.getEffectiveStatBonus();
            }
        }

        return total;
    }

    public int getCombinedStat(PlayerStat stat) {
        return getStat(stat) + getEquipmentStatBonus(stat);
    }

    public int getBaseStat(PlayerStat stat) {
        return stats.getOrDefault(stat, 0);
    }

    public Map<PlayerStat, Integer> getStatsView() {
        return Map.copyOf(stats);
    }

    public void setStat(PlayerStat stat, int value) {
        if (stat != null) {
            stats.put(stat, Math.max(0, value));
            recalculateMaxHp(false);
        }
    }

    public List<BattleSkill> getBattleSkills() {
        return List.copyOf(battleSkills);
    }

    public void learnSkill(SkillLibrary skill) {
        if (skill != null) {
            battleSkills.add(skill.createSkill());
        }
    }

    public void levelUp(Map<PlayerStat, Integer> chosenStatPoints) {
        level++;
        availableStatPoints += 10;

        if (chosenStatPoints != null) {
            chosenStatPoints.forEach((stat, amount) -> {
                int points = Math.max(0, amount == null ? 0 : amount);
                int spent = Math.min(points, availableStatPoints);

                if (spent > 0) {
                    addStat(stat, spent);
                    availableStatPoints -= spent;
                }
            });
        }

        recalculateMaxHp(true);
    }

    private void addStat(PlayerStat stat, int amount) {
        if (stat != null && amount > 0) {
            stats.put(stat, getBaseStat(stat) + amount);
        }
    }

    public boolean spendStatPoint(PlayerStat stat) {
        if (stat == null || availableStatPoints <= 0) {
            return false;
        }

        addStat(stat, 1);
        availableStatPoints--;
        recalculateMaxHp(stat == PlayerStat.VITALITY);
        return true;
    }

    public int getSkillLevel(CharacterSkill skill) {
        return skills.getOrDefault(skill, 0);
    }

    public void setSkillLevel(CharacterSkill skill, int level) {
        if (skill != null) {
            skills.put(skill, Math.max(0, level));
        }
    }

    public int getSkillExperience(CharacterSkill skill) {
        return skillExperience.getOrDefault(skill, 0);
    }

    public void setSkillExperience(CharacterSkill skill, int experience) {
        if (skill != null) {
            skillExperience.put(skill, Math.max(0, experience));
        }
    }

    public int addSkillExperience(CharacterSkill skill, int amount) {
        if (skill == null || amount <= 0) {
            return 0;
        }

        int experience = getSkillExperience(skill) + amount;
        int levelsGained = 0;
        int level = Math.max(1, getSkillLevel(skill));

        while (experience >= skillExperienceRequired(level)) {
            experience -= skillExperienceRequired(level);
            level++;
            levelsGained++;
        }

        skills.put(skill, level);
        skillExperience.put(skill, experience);
        return levelsGained;
    }

    public int getSkillExperienceRequired(CharacterSkill skill) {
        return skillExperienceRequired(Math.max(1, getSkillLevel(skill)));
    }

    private int skillExperienceRequired(int level) {
        return Math.max(1, level * 100);
    }

    public Map<CharacterSkill, Integer> getSkillsView() {
        return Map.copyOf(skills);
    }

    public Map<CharacterSkill, Integer> getSkillExperienceView() {
        return Map.copyOf(skillExperience);
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getCurrHp() {
        return currHp;
    }

    public void setCurrHp(int currHp) {
        this.currHp = clampHp(currHp);
    }

    public void takeDamage(int amount) {
        if (amount > 0) {
            setCurrHp(currHp - amount);
        }
    }

    public void heal(int amount) {
        if (amount > 0) {
            setCurrHp(currHp + amount);
        }
    }

    public InventorySystem.Inventory getInventory() {
        return inventory;
    }

    public Map<LimbSlot, LimbItem> getEquippedLimbsView() {
        return Map.copyOf(equippedLimbs);
    }

    public LimbItem getEquippedLimb(LimbSlot slot) {
        return equippedLimbs.get(slot);
    }

    public void equipLimb(LimbItem limb) {
        if (limb == null || limb.getLimbSlot() == null) {
            return;
        }

        equippedLimbs.put(limb.getLimbSlot(), limb);
        refreshBattleSkillsFromLimbs();
        recalculateMaxHp(false);
    }

    public boolean canWieldWeapon() {
        LimbItem leftArm = equippedLimbs.get(LimbSlot.LEFT_ARM);
        LimbItem rightArm = equippedLimbs.get(LimbSlot.RIGHT_ARM);
        return leftArm != null && rightArm != null && !leftArm.isBroken() && !rightArm.isBroken();
    }

    public boolean canUseEquipmentSlot(InventorySystem.EquipmentSlot slot) {
        if (slot == null) {
            return false;
        }

        return switch (slot) {
            case HEAD -> hasFunctionalLimb(LimbSlot.HEAD);
            case CHEST -> hasFunctionalLimb(LimbSlot.BODY);
            case LEGS -> hasFunctionalLimb(LimbSlot.LEGS);
            case RING_LEFT, RING_RIGHT -> hasFunctionalLimb(LimbSlot.LEFT_ARM) || hasFunctionalLimb(LimbSlot.RIGHT_ARM);
            case WEAPON -> canWieldWeapon();
            case SHIELD -> hasFunctionalLimb(LimbSlot.LEFT_ARM) || hasFunctionalLimb(LimbSlot.RIGHT_ARM);
        };
    }

    public boolean canUseEquipment(InventorySystem.Item item, InventorySystem.EquipmentSlot slot) {
        if (item == null || slot == null || !canUseEquipmentSlot(slot)) {
            return false;
        }

        if (item.getItemType() == InventorySystem.ItemType.WEAPON || item.getItemType() == InventorySystem.ItemType.RING) {
            return true;
        }

        return getSkillLevel(CharacterSkill.DEFENSE) >= defenseRequirementFor(item.getMaterial());
    }

    public static int defenseRequirementFor(GearMaterial material) {
        if (material == null) {
            return 0;
        }

        return switch (material) {
            case NONE -> 0;
            case BRONZE, OAK, LEATHER -> 1;
            case IRON, YEW, SILVER -> 5;
            case STEEL, IRONWOOD -> 10;
        };
    }

    public int getUsableWeaponStatBonus() {
        if (!canWieldWeapon()) {
            return 0;
        }

        return inventory.getWeaponStatBonus();
    }

    public int getUsableArmorStatBonus() {
        int total = 0;

        for (Map.Entry<InventorySystem.EquipmentSlot, InventorySystem.Item> entry : inventory.getEquippedItemsView().entrySet()) {
            if (entry.getKey() != InventorySystem.EquipmentSlot.WEAPON
                    && entry.getKey() != InventorySystem.EquipmentSlot.RING_LEFT
                    && entry.getKey() != InventorySystem.EquipmentSlot.RING_RIGHT
                    && entry.getValue() != null
                    && canUseEquipment(entry.getValue(), entry.getKey())) {
                total += entry.getValue().getEffectiveStatBonus();
            }
        }

        return total;
    }

    public int getUsableMagicAccuracyBonus() {
        int total = 0;

        for (Map.Entry<InventorySystem.EquipmentSlot, InventorySystem.Item> entry : inventory.getEquippedItemsView().entrySet()) {
            if ((entry.getKey() == InventorySystem.EquipmentSlot.RING_LEFT
                    || entry.getKey() == InventorySystem.EquipmentSlot.RING_RIGHT)
                    && entry.getValue() != null
                    && entry.getValue().getStatBonusTarget() == null
                    && canUseEquipment(entry.getValue(), entry.getKey())) {
                total += entry.getValue().getEffectiveStatBonus();
            }
        }

        return total;
    }

    public int getMeleeAccuracy() {
        return getCombinedStat(PlayerStat.ATTACK)
                + getSkillLevel(CharacterSkill.ATTACK)
                + getUsableWeaponStatBonus();
    }

    public int getMeleePower() {
        return getCombinedStat(PlayerStat.STRENGTH)
                + getSkillLevel(CharacterSkill.STRENGTH)
                + getUsableWeaponStatBonus();
    }

    public int getDefenseRoll() {
        return getCombinedStat(PlayerStat.DEFENSE)
                + getSkillLevel(CharacterSkill.DEFENSE)
                + getUsableArmorStatBonus();
    }

    public int getSpellcasting() {
        return getCombinedStat(PlayerStat.INTELLIGENCE)
                + getSkillLevel(CharacterSkill.MAGIC_ACCURACY)
                + getUsableMagicAccuracyBonus();
    }

    public int getSpellPotency() {
        return getCombinedStat(PlayerStat.WILLPOWER)
                + getSkillLevel(CharacterSkill.MAGIC_POWER);
    }

    private boolean hasFunctionalLimb(LimbSlot slot) {
        LimbItem limb = equippedLimbs.get(slot);
        return limb != null && !limb.isBroken();
    }

    public void equipStarterLimbs(List<LimbItem> limbs) {
        equippedLimbs.clear();

        if (limbs != null) {
            for (LimbItem limb : limbs) {
                equipLimb(limb);
            }
        }

        refreshBattleSkillsFromLimbs();
        recalculateMaxHp(true);
    }

    public boolean isDebugSkillsLoaded() {
        return debugSkillsLoaded;
    }

    public void loadDebugSkills() {
        debugSkillsLoaded = true;
        refreshBattleSkillsFromLimbs();
    }

    public String getPortraitPath() {
        return portraitPath;
    }

    private void refreshBattleSkillsFromLimbs() {
        battleSkills.clear();
        battleSkills.addAll(SkillLibrary.createUniversalPlayerSkills());

        if (debugSkillsLoaded) {
            battleSkills.addAll(SkillLibrary.createDebugPlayerSkills());
        }

        for (LimbItem limb : equippedLimbs.values()) {
            if (limb != null && !limb.isBroken()) {
                battleSkills.addAll(limb.getSkills());
            }
        }
    }

    private void recalculateMaxHp(boolean healToFull) {
        int newMaxHp = Math.max(MIN_MAX_HP, getStat(PlayerStat.VITALITY));
        int previousMaxHp = maxHp;
        maxHp = newMaxHp;

        if (healToFull || currHp > maxHp) {
            currHp = maxHp;
        } else if (newMaxHp > previousMaxHp) {
            currHp = Math.min(maxHp, currHp + (newMaxHp - previousMaxHp));
        }
    }

    private int clampHp(int hp) {
        return Math.max(MIN_CURRENT_HP, Math.min(maxHp, hp));
    }
}
