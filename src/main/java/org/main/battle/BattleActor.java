package org.main.battle;

import org.main.core.Library;
import org.main.core.CharacterSkill;
import org.main.core.PlayerCharacter;
import org.main.core.PlayerStat;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class BattleActor {
    private final String name;
    private final int maxHp;
    private final BufferedImage image;
    private int currentHp;
    private int slot = 0;
    private Library.BattleRow row = Library.BattleRow.FRONT;
    private final Library.EntityType EntityType;
    private final int attackDamage;
    private final int defense;
    private String attackSoundPath;
    private String hitSoundPath;
    private int defendingTurns = 0;
    private double defenseDamageReduction = 0.0;
    private int intelligence = 0;
    private String speciesId;
    private int experienceReward = 0;
    private int attackStat = 1;
    private int strengthStat = 1;
    private int defenseStat = 0;
    private int agilityStat = 1;
    private int willpowerStat = 1;
    private int weaponBonus = 0;
    private int armorBonus = 0;
    private final EnumMap<CharacterSkill, Integer> combatSkills = new EnumMap<>(CharacterSkill.class);
    private PlayerCharacter sourcePlayer;

    private final List<BattleSkill> skills = new ArrayList<>();
    private final Map<BattleStatusType, BattleStatus> statuses = new EnumMap<>(BattleStatusType.class);

    public BattleActor(String name, int maxHp, int currentHp, BufferedImage image, Library.EntityType entityType) {
        this(name, maxHp, currentHp, image, entityType, 5);
    }

    public BattleActor(
            String name,
            int maxHp,
            int currentHp,
            BufferedImage image,
            Library.EntityType entityType,
            int attackDamage
    ) {
        this(name, maxHp, currentHp, image, entityType, attackDamage, 0);
    }

    public BattleActor(
            String name,
            int maxHp,
            int currentHp,
            BufferedImage image,
            Library.EntityType entityType,
            int attackDamage,
            int defense
    ) {
        this.name = name;
        this.maxHp = maxHp;
        this.currentHp = currentHp;
        this.image = image;
        this.EntityType = entityType;
        this.attackDamage = attackDamage;
        this.defense = Math.max(0, defense);
        this.attackStat = Math.max(1, attackDamage);
        this.strengthStat = Math.max(1, attackDamage);
        this.defenseStat = Math.max(0, defense);
        this.armorBonus = Math.max(0, defense);
    }

    public List<BattleSkill> getSkills() {
        return Collections.unmodifiableList(skills);
    }

    public void addSkill(BattleSkill skill) {
        if (skill != null) {
            skills.add(skill);
        }
    }

    public Library.EntityType getEntityType() {
        return EntityType;
    }

    public Library.BattleRow getRow() {
        return row;
    }

    public int getSlot() {
        return slot;
    }

    public void setBattlePosition(Library.BattleRow row, int slot) {
        this.row = row;
        this.slot = slot;
    }

    public String getName() {
        return name;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public void setCurrentHp(int currentHp) {
        this.currentHp = Math.max(0, Math.min(maxHp, currentHp));
    }

    public BufferedImage getImage() {
        return image;
    }

    public boolean isEnemy() {
        return EntityType == Library.EntityType.ENEMY;
    }

    public String getAttackSoundPath() {
        return attackSoundPath;
    }

    public void setAttackSoundPath(String attackSoundPath) {
        this.attackSoundPath = attackSoundPath;
    }

    public String getHitSoundPath() {
        return hitSoundPath;
    }

    public void setHitSoundPath(String hitSoundPath) {
        this.hitSoundPath = hitSoundPath;
    }

    public int getAttackDamage() {
        return attackDamage;
    }

    public int takeDamage(int amount) {
        int adjustedAmount = amount;

        if (defendingTurns > 0) {
            adjustedAmount = (int) Math.ceil(amount * (1.0 - defenseDamageReduction));
        }

        adjustedAmount = Math.max(0, adjustedAmount);
        currentHp = Math.max(0, currentHp - adjustedAmount);
        return adjustedAmount;
    }

    public void healDamage(int amount) {
        currentHp = Math.min(maxHp, currentHp + amount);
    }

    public boolean isAlive() {
        return currentHp > 0;
    }

    public boolean isStunned() {
        return hasStatus(BattleStatusType.STUN);
    }

    public int getStunnedTurns() {
        BattleStatus status = statuses.get(BattleStatusType.STUN);
        return status == null ? 0 : status.getRemainingTurns();
    }

    public int getDefendingTurns() {
        return defendingTurns;
    }

    public void applyStun(int turns) {
        applyStatus(BattleStatusType.STUN, turns);
    }

    public void applyStatus(BattleStatusType type, int turns) {
        if (type == null || turns <= 0) {
            return;
        }

        BattleStatus status = statuses.get(type);

        if (status == null) {
            statuses.put(type, new BattleStatus(type, turns));
            return;
        }

        status.refresh(turns);
    }

    public boolean hasStatus(BattleStatusType type) {
        BattleStatus status = statuses.get(type);
        return status != null && !status.isExpired();
    }

    public List<BattleStatus> getStatuses() {
        return List.copyOf(statuses.values());
    }

    public void applyDefend(int turns, double damageReduction) {
        defendingTurns = Math.max(defendingTurns, turns);
        defenseDamageReduction = Math.max(defenseDamageReduction, damageReduction);
    }

    public void tickStatusDurations() {
        if (defendingTurns > 0) {
            defendingTurns--;
        }

        if (defendingTurns == 0) {
            defenseDamageReduction = 0.0;
        }

        Iterator<BattleStatus> iterator = statuses.values().iterator();

        while (iterator.hasNext()) {
            BattleStatus status = iterator.next();
            status.tick();

            if (status.isExpired()) {
                iterator.remove();
            }
        }
    }

    public int getIntelligence() {
        return intelligence;
    }

    public void setIntelligence(int intelligence) {
        this.intelligence = Math.max(0, intelligence);
    }

    public int getAttackStat() {
        return attackStat;
    }

    public void setAttackStat(int attackStat) {
        this.attackStat = Math.max(0, attackStat);
    }

    public int getStrengthStat() {
        return strengthStat;
    }

    public void setStrengthStat(int strengthStat) {
        this.strengthStat = Math.max(0, strengthStat);
    }

    public int getDefenseStat() {
        return defenseStat;
    }

    public void setDefenseStat(int defenseStat) {
        this.defenseStat = Math.max(0, defenseStat);
    }

    public int getAgilityStat() {
        return agilityStat;
    }

    public void setAgilityStat(int agilityStat) {
        this.agilityStat = Math.max(0, agilityStat);
    }

    public int getWillpowerStat() {
        return willpowerStat;
    }

    public void setWillpowerStat(int willpowerStat) {
        this.willpowerStat = Math.max(0, willpowerStat);
    }

    public int getWeaponBonus() {
        return weaponBonus;
    }

    public void setWeaponBonus(int weaponBonus) {
        this.weaponBonus = Math.max(0, weaponBonus);
    }

    public int getArmorBonus() {
        return armorBonus;
    }

    public void setArmorBonus(int armorBonus) {
        this.armorBonus = Math.max(0, armorBonus);
    }

    public int getCombatSkillLevel(CharacterSkill skill) {
        return combatSkills.getOrDefault(skill, 1);
    }

    public void setCombatSkillLevel(CharacterSkill skill, int level) {
        if (skill != null) {
            combatSkills.put(skill, Math.max(1, level));
        }
    }

    public void copyCombatProfileFrom(PlayerCharacter player) {
        if (player == null) {
            return;
        }

        sourcePlayer = player;
        setAttackStat(player.getCombinedStat(PlayerStat.ATTACK));
        setStrengthStat(player.getCombinedStat(PlayerStat.STRENGTH));
        setDefenseStat(player.getCombinedStat(PlayerStat.DEFENSE));
        setAgilityStat(player.getCombinedStat(PlayerStat.AGILITY));
        setIntelligence(player.getCombinedStat(PlayerStat.INTELLIGENCE));
        setWillpowerStat(player.getCombinedStat(PlayerStat.WILLPOWER));
        setWeaponBonus(player.getUsableWeaponStatBonus());
        setArmorBonus(player.getUsableArmorStatBonus());
        setCombatSkillLevel(CharacterSkill.ATTACK, player.getSkillLevel(CharacterSkill.ATTACK));
        setCombatSkillLevel(CharacterSkill.STRENGTH, player.getSkillLevel(CharacterSkill.STRENGTH));
        setCombatSkillLevel(CharacterSkill.DEFENSE, player.getSkillLevel(CharacterSkill.DEFENSE));
        setCombatSkillLevel(CharacterSkill.MAGIC_ACCURACY,
                player.getSkillLevel(CharacterSkill.MAGIC_ACCURACY) + player.getUsableMagicAccuracyBonus());
        setCombatSkillLevel(CharacterSkill.MAGIC_POWER, player.getSkillLevel(CharacterSkill.MAGIC_POWER));
    }

    public void configureMonsterCombatStats(Map<PlayerStat, Integer> stats) {
        int attack = statValue(stats, PlayerStat.ATTACK);
        int strength = statValue(stats, PlayerStat.STRENGTH);
        int defense = statValue(stats, PlayerStat.DEFENSE);
        int agility = statValue(stats, PlayerStat.AGILITY);
        int intelligence = statValue(stats, PlayerStat.INTELLIGENCE);
        int willpower = statValue(stats, PlayerStat.WILLPOWER);

        setAttackStat(attack);
        setStrengthStat(strength);
        setDefenseStat(defense);
        setAgilityStat(agility);
        setIntelligence(intelligence);
        setWillpowerStat(willpower);
        setArmorBonus(defense);
        setCombatSkillLevel(CharacterSkill.ATTACK, Math.max(1, attack));
        setCombatSkillLevel(CharacterSkill.STRENGTH, Math.max(1, strength));
        setCombatSkillLevel(CharacterSkill.DEFENSE, Math.max(1, defense));
        setCombatSkillLevel(CharacterSkill.MAGIC_ACCURACY, Math.max(1, intelligence));
        setCombatSkillLevel(CharacterSkill.MAGIC_POWER, Math.max(1, willpower));
    }

    private int statValue(Map<PlayerStat, Integer> stats, PlayerStat stat) {
        return Math.max(0, stats == null ? 0 : stats.getOrDefault(stat, 0));
    }

    public void addCombatSkillExperience(CharacterSkill skill, int amount) {
        if (sourcePlayer == null || skill == null || amount <= 0) {
            return;
        }

        int levelsGained = sourcePlayer.addSkillExperience(skill, amount);
        setCombatSkillLevel(skill, sourcePlayer.getSkillLevel(skill));

        if (levelsGained > 0 && skill == CharacterSkill.DEFENSE) {
            setArmorBonus(sourcePlayer.getUsableArmorStatBonus());
        }
    }

    public String getSpeciesId() {
        return speciesId;
    }

    public void setSpeciesId(String speciesId) {
        this.speciesId = speciesId;
    }

    public int getExperienceReward() {
        return experienceReward;
    }

    public void setExperienceReward(int experienceReward) {
        this.experienceReward = Math.max(0, experienceReward);
    }
}
