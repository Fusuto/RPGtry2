package org.main.core;

import java.util.HashMap;
import java.util.Map;

public class PlayerCharacter {
    private final String name;
    private final HashMap<CharacterSkill, Integer> skills;
    private final int maxHp;
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
        this.name = name;
        this.maxHp = Math.max(1, maxHp);
        this.currHp = clampHp(currHp);
        this.inventory = inventory == null ? new InventorySystem.Inventory() : inventory;
        this.skills = skills == null ? createDefaultSkills() : skills;
        this.portraitPath = portraitPath;
    }

    public static HashMap<CharacterSkill, Integer> createDefaultSkills() {
        HashMap<CharacterSkill, Integer> defaultSkills = new HashMap<>();

        for (CharacterSkill skill : CharacterSkill.values()) {
            defaultSkills.put(skill, 1);
        }

        return defaultSkills;
    }

    public String getName() {
        return name;
    }

    public HashMap<CharacterSkill, Integer> getSkills() {
        return skills;
    }

    public int getSkillLevel(CharacterSkill skill) {
        return skills.getOrDefault(skill, 0);
    }

    public void setSkillLevel(CharacterSkill skill, int level) {
        if (skill != null) {
            skills.put(skill, Math.max(0, level));
        }
    }

    public Map<CharacterSkill, Integer> getSkillsView() {
        return Map.copyOf(skills);
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
