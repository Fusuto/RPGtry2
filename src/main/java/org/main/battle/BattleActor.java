package org.main.battle;

import org.main.core.Library;
import org.main.core.CharacterSkill;
import org.main.core.GameConfiguration;
import org.main.core.PlayerCharacter;
import org.main.core.PlayerStat;
import org.main.core.PartyRoster;
import org.main.content.CharacterModelDefinition;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
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
    private boolean battlePositionAssigned;
    private final Library.EntityType EntityType;
    private final int attackDamage;
    private final int defense;
    private String attackSoundPath;
    private String hitSoundPath;
    private int defendingTurns = 0;
    private double defenseDamageReduction = 0.0;
    private int intelligence = 0;
    private int combatAiIntelligence = 0;
    private String speciesId;
    private int experienceReward = 0;
    private int attackStat = 1;
    private int strengthStat = 1;
    private int defenseStat = 0;
    private int agilityStat = 1;
    private int willpowerStat = 1;
    private int weaponAccuracyBonus = 0;
    private int weaponPowerBonus = 0;
    private double weaponSpeedMultiplier = 1.0;
    private int armorBonus = 0;
    private double attackCooldownRemainingSeconds = 0.0;
    private BattleActor preferredAutoAttackTarget;
    private final EnumMap<CharacterSkill, Integer> combatSkills = new EnumMap<>(CharacterSkill.class);
    private final Map<String, Double> skillCooldowns = new HashMap<>();
    private PlayerCharacter sourcePlayer;
    private PlayerCharacter formationOwner;
    private CharacterModelDefinition characterModel = CharacterModelDefinition.empty();
    private String partyMemberId = "";

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
        this.battlePositionAssigned = true;
    }

    public boolean hasBattlePositionAssignment() {
        return battlePositionAssigned;
    }

    public void clearBattlePositionAssignment() {
        battlePositionAssigned = false;
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

    public void resetAttackCooldown() {
        attackCooldownRemainingSeconds = BattleTiming.calculateAttackIntervalSeconds(getAgilityStat(), getWeaponSpeedMultiplier());
    }

    public void tickAttackCooldown(double deltaSeconds) {
        attackCooldownRemainingSeconds = Math.max(0.0, attackCooldownRemainingSeconds - Math.max(0.0, deltaSeconds));
    }

    public void tickSkillCooldowns(double deltaSeconds) {
        double safeDelta = Math.max(0.0, deltaSeconds);
        if (safeDelta <= 0.0 || skillCooldowns.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<String, Double>> iterator = skillCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Double> entry = iterator.next();
            double remaining = Math.max(0.0, entry.getValue() - safeDelta);
            if (remaining <= 0.0) {
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    public boolean isSkillReady(BattleSkill skill) {
        return getSkillCooldownRemainingSeconds(skill) <= 0.0;
    }

    public double getSkillCooldownRemainingSeconds(BattleSkill skill) {
        if (skill == null || skill.getBaseCooldownSeconds() <= 0.0) {
            return 0.0;
        }
        return Math.max(0.0, skillCooldowns.getOrDefault(skill.getSkillId(), 0.0));
    }

    public void startSkillCooldown(BattleSkill skill) {
        if (skill == null || skill.getBaseCooldownSeconds() <= 0.0) {
            return;
        }

        double cooldownSeconds = effectiveSkillCooldownSeconds(skill);
        if (cooldownSeconds > 0.0) {
            skillCooldowns.put(skill.getSkillId(), cooldownSeconds);
        }
    }

    private double effectiveSkillCooldownSeconds(BattleSkill skill) {
        double reductionPerPoint = Math.max(0.0, GameConfiguration.doubleValue(
                "battle.skillCooldown.willpowerReductionPerPoint",
                0.02
        ));
        double minimumMultiplier = Math.max(0.0, Math.min(1.0, GameConfiguration.doubleValue(
                "battle.skillCooldown.minimumMultiplier",
                0.50
        )));
        double multiplier = Math.max(
                minimumMultiplier,
                1.0 - Math.max(0, getWillpowerStat() - 1) * reductionPerPoint
        );
        return Math.max(0.0, skill.getBaseCooldownSeconds() * multiplier);
    }

    public boolean isAttackReady() {
        return attackCooldownRemainingSeconds <= 0.0;
    }

    public double getAttackCooldownRemainingSeconds() {
        return attackCooldownRemainingSeconds;
    }

    public double getAttackCooldownProgress() {
        double interval = BattleTiming.calculateAttackIntervalSeconds(getAgilityStat(), getWeaponSpeedMultiplier());
        if (interval <= 0.0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, 1.0 - attackCooldownRemainingSeconds / interval));
    }

    public BattleActor getPreferredAutoAttackTarget() {
        return preferredAutoAttackTarget;
    }

    public void setPreferredAutoAttackTarget(BattleActor preferredAutoAttackTarget) {
        this.preferredAutoAttackTarget = preferredAutoAttackTarget;
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
        applyStatus(type, turns, 0);
    }

    public void applyStatus(BattleStatusType type, int turns, int potency) {
        if (type == null || turns <= 0) {
            return;
        }

        int effectivePotency = potency > 0 ? potency : type.getDefaultPotency();
        BattleStatus status = statuses.get(type);

        if (status == null) {
            statuses.put(type, new BattleStatus(type, turns, effectivePotency));
            return;
        }

        status.refresh(turns, effectivePotency);
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
        return adjustedStat(intelligence, PlayerStat.INTELLIGENCE);
    }

    public void setIntelligence(int intelligence) {
        this.intelligence = Math.max(0, intelligence);
        this.combatAiIntelligence = Math.max(this.combatAiIntelligence, this.intelligence);
    }

    public int getCombatAiIntelligence() {
        return combatAiIntelligence;
    }

    public void setCombatAiIntelligence(int combatAiIntelligence) {
        this.combatAiIntelligence = Math.max(0, combatAiIntelligence);
    }

    public int getAttackStat() {
        return adjustedStat(attackStat, PlayerStat.ATTACK);
    }

    public void setAttackStat(int attackStat) {
        this.attackStat = Math.max(0, attackStat);
    }

    public int getStrengthStat() {
        return adjustedStat(strengthStat, PlayerStat.STRENGTH);
    }

    public void setStrengthStat(int strengthStat) {
        this.strengthStat = Math.max(0, strengthStat);
    }

    public int getDefenseStat() {
        return adjustedStat(defenseStat, PlayerStat.DEFENSE);
    }

    public void setDefenseStat(int defenseStat) {
        this.defenseStat = Math.max(0, defenseStat);
    }

    public int getAgilityStat() {
        return adjustedStat(agilityStat, PlayerStat.AGILITY);
    }

    private int adjustedStat(int baseValue, PlayerStat stat) {
        int adjustedValue = baseValue;
        for (BattleStatus status : statuses.values()) {
            if (!status.isExpired() && status.getType().getAffectedStat() == stat) {
                adjustedValue -= status.getPotency();
            }
        }
        return Math.max(0, adjustedValue);
    }

    public void setAgilityStat(int agilityStat) {
        this.agilityStat = Math.max(0, agilityStat);
    }

    public int getWillpowerStat() {
        return adjustedStat(willpowerStat, PlayerStat.WILLPOWER);
    }

    public void setWillpowerStat(int willpowerStat) {
        this.willpowerStat = Math.max(0, willpowerStat);
    }

    public int getWeaponBonus() {
        return Math.max(weaponAccuracyBonus, weaponPowerBonus);
    }

    public void setWeaponBonus(int weaponBonus) {
        int safeBonus = Math.max(0, weaponBonus);
        this.weaponAccuracyBonus = safeBonus;
        this.weaponPowerBonus = safeBonus;
        this.weaponSpeedMultiplier = 1.0;
    }

    public int getWeaponAccuracyBonus() {
        return weaponAccuracyBonus;
    }

    public void setWeaponAccuracyBonus(int weaponAccuracyBonus) {
        this.weaponAccuracyBonus = Math.max(0, weaponAccuracyBonus);
    }

    public int getWeaponPowerBonus() {
        return weaponPowerBonus;
    }

    public void setWeaponPowerBonus(int weaponPowerBonus) {
        this.weaponPowerBonus = Math.max(0, weaponPowerBonus);
    }

    public double getWeaponSpeedMultiplier() {
        return weaponSpeedMultiplier;
    }

    public void setWeaponSpeedMultiplier(double weaponSpeedMultiplier) {
        this.weaponSpeedMultiplier = Double.isFinite(weaponSpeedMultiplier) && weaponSpeedMultiplier > 0.0
                ? weaponSpeedMultiplier
                : 1.0;
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
        formationOwner = player;
        partyMemberId = PartyRoster.PLAYER_MEMBER_ID;
        setAttackStat(player.getCombinedStat(PlayerStat.ATTACK));
        setStrengthStat(player.getCombinedStat(PlayerStat.STRENGTH));
        setDefenseStat(player.getCombinedStat(PlayerStat.DEFENSE));
        setAgilityStat(player.getCombinedStat(PlayerStat.AGILITY));
        setIntelligence(player.getCombinedStat(PlayerStat.INTELLIGENCE));
        setWillpowerStat(player.getCombinedStat(PlayerStat.WILLPOWER));
        setWeaponAccuracyBonus(player.getUsableWeaponAccuracyBonus());
        setWeaponPowerBonus(player.getUsableWeaponPowerBonus());
        setWeaponSpeedMultiplier(player.getUsableWeaponSpeedMultiplier());
        setArmorBonus(player.getUsableArmorStatBonus());
        setCombatSkillLevel(CharacterSkill.ATTACK, player.getSkillLevel(CharacterSkill.ATTACK));
        setCombatSkillLevel(CharacterSkill.STRENGTH, player.getSkillLevel(CharacterSkill.STRENGTH));
        setCombatSkillLevel(CharacterSkill.DEFENSE, player.getSkillLevel(CharacterSkill.DEFENSE));
        setCombatSkillLevel(CharacterSkill.MAGIC_ACCURACY,
                player.getSkillLevel(CharacterSkill.MAGIC_ACCURACY) + player.getUsableMagicAccuracyBonus());
        setCombatSkillLevel(CharacterSkill.MAGIC_POWER, player.getSkillLevel(CharacterSkill.MAGIC_POWER));
    }

    public PlayerCharacter getSourcePlayer() {
        return sourcePlayer;
    }

    public String getPartyMemberId() {
        return partyMemberId;
    }

    public PlayerCharacter getFormationOwner() {
        return formationOwner;
    }

    /** Hook for future permanent companions; it does not make them the XP-receiving player actor. */
    public void bindPartyFormation(PlayerCharacter owner, String memberId) {
        formationOwner = owner;
        setPartyMemberId(memberId);
    }

    public void setPartyMemberId(String partyMemberId) {
        this.partyMemberId = partyMemberId == null ? "" : partyMemberId.trim();
    }

    public CharacterModelDefinition getCharacterModel() {
        return characterModel;
    }

    public void setCharacterModel(CharacterModelDefinition characterModel) {
        this.characterModel = characterModel == null
                ? CharacterModelDefinition.empty()
                : characterModel;
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

    public BattleActor copyForSummon(String summonedName) {
        BattleActor copy = new BattleActor(
                summonedName == null || summonedName.isBlank() ? name : summonedName,
                maxHp,
                maxHp,
                image,
                EntityType,
                attackDamage,
                defense
        );
        copy.setAttackSoundPath(attackSoundPath);
        copy.setHitSoundPath(hitSoundPath);
        copy.setAttackStat(attackStat);
        copy.setStrengthStat(strengthStat);
        copy.setDefenseStat(defenseStat);
        copy.setAgilityStat(agilityStat);
        copy.setIntelligence(intelligence);
        copy.setCombatAiIntelligence(combatAiIntelligence);
        copy.setWillpowerStat(willpowerStat);
        copy.setWeaponAccuracyBonus(weaponAccuracyBonus);
        copy.setWeaponPowerBonus(weaponPowerBonus);
        copy.setWeaponSpeedMultiplier(weaponSpeedMultiplier);
        copy.setArmorBonus(armorBonus);
        copy.setSpeciesId(speciesId);
        copy.setExperienceReward(experienceReward);
        copy.setCharacterModel(characterModel);
        copy.setPartyMemberId(partyMemberId);
        for (Map.Entry<CharacterSkill, Integer> entry : combatSkills.entrySet()) {
            copy.setCombatSkillLevel(entry.getKey(), entry.getValue());
        }
        for (BattleSkill skill : skills) {
            copy.addSkill(skill);
        }
        copy.resetAttackCooldown();
        return copy;
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
