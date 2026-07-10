package org.main.core;

import org.main.battle.BattleSkill;
import org.main.content.PlayerClassLibrary;
import org.main.content.SkillLibrary;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerCharacter {
    private final String name;
    private final HashMap<CharacterSkill, Integer> skills;
    private final HashMap<CharacterSkill, Integer> skillExperience = new HashMap<>();
    private final EnumMap<PlayerStat, Integer> stats;
    private final List<BattleSkill> battleSkills;
    private final PlayerClassLibrary playerClass;
    private int level = 1;
    private int classExperience = 0;
    private int availableStatPoints = 0;
    private int maxHp;
    private int currHp;
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
            PlayerClassLibrary playerClass,
            Map<PlayerStat, Integer> stats,
            List<BattleSkill> battleSkills
    ) {
        this.name = name == null || name.isBlank() ? "Player" : name.trim();
        this.maxHp = Math.max(1, maxHp);
        this.currHp = clampHp(currHp);
        this.inventory = inventory == null ? new InventorySystem.Inventory() : inventory;
        this.skills = skills == null ? createDefaultSkills() : skills;
        this.portraitPath = portraitPath;
        this.playerClass = playerClass;
        this.stats = new EnumMap<>(PlayerStat.class);

        for (PlayerStat stat : PlayerStat.values()) {
            this.stats.put(stat, Math.max(0, stats == null ? 1 : stats.getOrDefault(stat, 1)));
        }

        this.battleSkills = new ArrayList<>();

        if (battleSkills != null) {
            this.battleSkills.addAll(battleSkills);
        }
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
            defaultStats.put(stat, 1);
        }

        return defaultStats;
    }

    public String getName() {
        return name;
    }

    public HashMap<CharacterSkill, Integer> getSkills() {
        return skills;
    }

    public PlayerClassLibrary getPlayerClass() {
        return playerClass;
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

    public void addClassExperience(int amount) {
        if (amount <= 0) {
            return;
        }

        classExperience += amount;

        while (classExperience >= getClassExperienceRequiredForNextLevel()) {
            classExperience -= getClassExperienceRequiredForNextLevel();
            levelUp(Map.of());
        }
    }

    public void restoreProgress(int level, int classExperience, int availableStatPoints) {
        this.level = Math.max(1, level);
        this.classExperience = Math.max(0, classExperience);
        this.availableStatPoints = Math.max(0, availableStatPoints);
    }

    public int getStat(PlayerStat stat) {
        return stats.getOrDefault(stat, 0);
    }

    public Map<PlayerStat, Integer> getStatsView() {
        return Map.copyOf(stats);
    }

    public void setStat(PlayerStat stat, int value) {
        if (stat != null) {
            stats.put(stat, Math.max(0, value));
            maxHp = Math.max(1, 20 + getStat(PlayerStat.VITALITY) * 5);
            currHp = clampHp(currHp);
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

        if (playerClass != null) {
            playerClass.getPreferredStatGrowth().forEach(this::addStat);
        }

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

        maxHp = Math.max(1, 20 + getStat(PlayerStat.VITALITY) * 5);
        currHp = maxHp;
    }

    private void addStat(PlayerStat stat, int amount) {
        if (stat != null && amount > 0) {
            stats.put(stat, getStat(stat) + amount);
        }
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

    public void addSkillExperience(CharacterSkill skill, int amount) {
        if (skill == null || amount <= 0) {
            return;
        }

        skillExperience.put(skill, getSkillExperience(skill) + amount);
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

    public String getPortraitPath() {
        return portraitPath;
    }

    private int clampHp(int hp) {
        return Math.max(0, Math.min(maxHp, hp));
    }
}
