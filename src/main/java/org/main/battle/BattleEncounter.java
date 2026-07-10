package org.main.battle;

import org.main.content.EnvironmentLibrary;
import org.main.core.GameBootstrap;
import org.main.core.InventorySystem;
import org.main.core.Library;
import org.main.core.PlayerCharacter;
import org.main.core.PlayerStat;
import org.main.engine.SoundSystem;
import org.main.monsters.Monster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BattleEncounter {
    private final List<BattleActor> allies;
    private final List<BattleActor> enemies;
    private final SoundSystem soundSystem;

    private String battleMessage = "Battle begins!";

    public BattleEncounter(List<BattleActor> allies, List<BattleActor> enemies) {
        this(allies, enemies, null);
    }

    public BattleEncounter(List<BattleActor> allies, List<BattleActor> enemies, SoundSystem soundSystem) {
        this.allies = allies;
        this.enemies = enemies;
        this.soundSystem = soundSystem;

        assignFormations();
    }

    private void assignFormations() {
        assignFormation(allies);
        assignFormation(enemies);
    }

    private void assignFormation(List<BattleActor> actors) {
        for (int i = 0; i < actors.size(); i++) {
            BattleActor actor = actors.get(i);

            int slot = i % 3;
            Library.BattleRow row = i < 3 ? Library.BattleRow.FRONT : Library.BattleRow.BACK;

            actor.setBattlePosition(row, slot);
        }
    }

    public static BattleEncounter fromMonster(List<Monster> monster) {
        return fromMonster(monster, (PlayerCharacter) null, null, null);
    }

    public static BattleEncounter fromMonster(
            List<Monster> monster,
            InventorySystem.Inventory inventory,
            SoundSystem soundSystem
    ) {
        return fromMonster(monster, inventory, soundSystem, null);
    }

    public static BattleEncounter fromMonster(
            List<Monster> monster,
            InventorySystem.Inventory inventory,
            SoundSystem soundSystem,
            EnvironmentLibrary environment
    ) {
        PlayerCharacter playerCharacter = GameBootstrap.createDefaultPlayerCharacter();

        if (inventory != null) {
            playerCharacter = new PlayerCharacter(
                    playerCharacter.getName(),
                    playerCharacter.getMaxHp(),
                    playerCharacter.getCurrHp(),
                    inventory,
                    playerCharacter.getSkills(),
                    playerCharacter.getPortraitPath()
            );
        }

        return fromMonster(monster, playerCharacter, soundSystem, environment);
    }

    public static BattleEncounter fromMonster(
            List<Monster> monster,
            PlayerCharacter playerCharacter,
            SoundSystem soundSystem,
            EnvironmentLibrary environment
    ) {
        if (playerCharacter == null) {
            playerCharacter = GameBootstrap.createDefaultPlayerCharacter();
        }

        int equipmentAttackBonus = playerCharacter.getInventory() == null
                ? 0
                : playerCharacter.getInventory().getWeaponStatBonus();
        int equipmentDefenseBonus = playerCharacter.getInventory() == null
                ? 0
                : playerCharacter.getInventory().getArmorStatBonus();

        BattleActor playerActor = new BattleActor(
                playerCharacter.getName(),
                playerCharacter.getMaxHp(),
                playerCharacter.getCurrHp(),
                null,
                Library.EntityType.ALLY,
                5 + playerCharacter.getStat(PlayerStat.STRENGTH) + equipmentAttackBonus,
                playerCharacter.getStat(PlayerStat.DEFENSE) + equipmentDefenseBonus
        );
        playerActor.setHitSoundPath(environment == null ? null : environment.getPlayerHitSoundPath());

        for (BattleSkill skill : playerCharacter.getBattleSkills()) {
            playerActor.addSkill(skill);
        }

        if (playerCharacter.getInventory() != null) {
            InventorySystem.Item weapon = playerCharacter.getInventory().getEquippedItem(InventorySystem.EquipmentSlot.WEAPON);

            if (weapon != null) {
                playerActor.setAttackSoundPath(weapon.getUseSoundPath());
            }
        }

        List<BattleActor> monsterActors = new ArrayList<>();

        monster.forEach(monster1 -> {
            var enemy = new BattleActor(
                    monster1.getName(),
                    monster1.getMaxHp(),
                    monster1.getCurrentHp(),
                    monster1.getType().getImg(),
                    Library.EntityType.ENEMY,
                    monster1.getAttack()
            );
            enemy.setAttackSoundPath(monster1.getType().getAttackSoundPath());
            enemy.setHitSoundPath(monster1.getType().getDamageSoundPath());
            enemy.setIntelligence(monster1.getType().getIntelligence());
            enemy.setSpeciesId(monster1.getType().name());
            enemy.setExperienceReward(monster1.getType().getXpReward());
            monster1.getType().getSkills().forEach(skill -> enemy.addSkill(skill.createSkill()));
            monsterActors.add(enemy);
        });

        return new BattleEncounter(
                List.of(playerActor),
                monsterActors,
                soundSystem
        );
    }

    public List<BattleActor> getSelectableActorsForSkill(
            BattleActor caster,
            BattleSkill skill
    ) {
        List<BattleActor> selectableActors = new ArrayList<>();

        if (caster == null || skill == null) {
            return selectableActors;
        }

        List<BattleActor> possibleTargets = getActorsForSkillTargetTeam(caster, skill.getTargetTeam());

        for (BattleActor possibleTarget : possibleTargets) {
            if (canSelectActorForSkill(caster, possibleTarget, skill)) {
                selectableActors.add(possibleTarget);
            }
        }

        return selectableActors;
    }

    public boolean canSelectActorForSkill(
            BattleActor caster,
            BattleActor selectedActor,
            BattleSkill skill
    ) {
        if (caster == null || selectedActor == null || skill == null) {
            return false;
        }

        if (!caster.isAlive() || !selectedActor.isAlive()) {
            return false;
        }

        List<BattleActor> validSide = getActorsForSkillTargetTeam(caster, skill.getTargetTeam());

        if (!validSide.contains(selectedActor)) {
            return false;
        }

        /*
         * Friendly skills should usually ignore front-row blocking.
         * Enemy-targeting skills can use melee/reach/ranged/magic targeting rules.
         */
        if (skill.getTargetTeam() == Library.EntityType.ALLY) {
            return true;
        }

        return canTarget(caster, selectedActor, skill.getTargetingMode());
    }

    public List<BattleActor> resolveSkillTargets(
            BattleActor caster,
            BattleActor selectedActor,
            BattleSkill skill
    ) {
        List<BattleActor> resolvedTargets = new ArrayList<>();

        if (!canSelectActorForSkill(caster, selectedActor, skill)) {
            return resolvedTargets;
        }

        List<BattleActor> possibleTargets = getActorsForSkillTargetTeam(caster, skill.getTargetTeam());

        for (BattleActor actor : possibleTargets) {
            if (!actor.isAlive()) {
                continue;
            }

            boolean shouldInclude = BattleTargetResolver.matchesSkillShape(actor, selectedActor, skill.getTargetShape());

            if (shouldInclude) {
                resolvedTargets.add(actor);
            }
        }

        return resolvedTargets;
    }

    private List<BattleActor> getActorsForSkillTargetTeam(
            BattleActor caster,
            Library.EntityType targetTeam
    ) {
        if (targetTeam == Library.EntityType.ALLY) {
            return getActorsOnSameSide(caster);
        }

        return getOpposingActors(caster);
    }

    public Library.BattleResult handleSkill(
            BattleActor caster,
            BattleSkill skill,
            List<BattleActor> targets
    ) {
        if (caster == null || skill == null) {
            battleMessage = "No skill selected.";
            return Library.BattleResult.CONTINUE;
        }

        if (targets == null || targets.isEmpty()) {
            battleMessage = "No valid targets.";
            return Library.BattleResult.CONTINUE;
        }

        battleMessage = caster.getName()
                + " uses "
                + skill.getName()
                + " on "
                + joinActorNames(targets)
                + ".";

        int totalDamage = 0;

        for (BattleActor target : targets) {
            if (skill.getEffectType().equals(Library.EffectType.DAMAGE)) {
                totalDamage += target.takeDamage(skill.getDamage());
                playSound(target.getHitSoundPath());

                if (skill.getStunTurns() > 0 && Math.random() < skill.getStunChance()) {
                    target.applyStun(skill.getStunTurns());
                }
            } else if (skill.getEffectType().equals(Library.EffectType.HEAL)) {
                target.healDamage(skill.getDamage());
            } else if (skill.getEffectType().equals(Library.EffectType.DEFEND)) {
                target.applyDefend(skill.getDefendTurns(), skill.getDamageReduction());
            }
        }

        if (skill.healsCasterFromDamage() && totalDamage > 0) {
            caster.healDamage((int) Math.ceil(totalDamage * skill.getSelfHealPercent()));
        }

        playSound(skill.getUseSoundPath());

        if (allEnemiesDefeated()) {
            battleMessage = "Victory!";
            return Library.BattleResult.VICTORY;
        }

        return handleEnemyTurn();
    }

    private String joinActorNames(List<BattleActor> actors) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < actors.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }

            builder.append(actors.get(i).getName());
        }

        return builder.toString();
    }

    public boolean canTargetWithMelee(BattleActor target, boolean hasReach) {
        if (hasReach) {
            return true;
        }

        return !isBlockedByFrontActor(target);
    }

    public Library.BattleResult handleRunCommand(Library.BattleCommand command) {
        return handleRun();
    }

    public List<BattleActor> getValidTargets(
            BattleActor attacker,
            Library.BattleTargetingMode targetingMode
    ) {
        List<BattleActor> validTargets = new ArrayList<>();

        for (BattleActor target : getOpposingActors(attacker)) {
            if (canTarget(attacker, target, targetingMode)) {
                validTargets.add(target);
            }
        }

        return validTargets;
    }

    public boolean canTarget(
            BattleActor attacker,
            BattleActor target,
            Library.BattleTargetingMode targetingMode
    ) {
        if (attacker == null || target == null) {
            return false;
        }

        if (!attacker.isAlive() || !target.isAlive()) {
            return false;
        }

        if (isSameSide(attacker, target)) {
            return false;
        }

        return switch (targetingMode) {
            case NORMAL_MELEE -> !isBlockedByFrontActor(target);
            case REACH_MELEE, RANGED, MAGIC -> true;
        };
    }

    public boolean isBlockedByFrontActor(BattleActor target) {
        if (target.getRow() != Library.BattleRow.BACK) {
            return false;
        }

        List<BattleActor> sameSideActors = getActorsOnSameSide(target);

        for (BattleActor actor : sameSideActors) {
            if (!actor.isAlive()) {
                continue;
            }

            boolean sameSlot = actor.getSlot() == target.getSlot();
            boolean isFrontRow = actor.getRow() == Library.BattleRow.FRONT;

            if (sameSlot && isFrontRow) {
                return true;
            }
        }

        return false;
    }

    private boolean isSameSide(BattleActor a, BattleActor b) {
        return a.getEntityType() == b.getEntityType();
    }

    private List<BattleActor> getActorsOnSameSide(BattleActor actor) {
        if (actor.isEnemy()) {
            return enemies;
        }

        return allies;
    }

    private List<BattleActor> getOpposingActors(BattleActor actor) {
        if (actor.isEnemy()) {
            return allies;
        }

        return enemies;
    }

    private Library.BattleResult handleRun() {
        battleMessage = "You ran away.";
        return Library.BattleResult.RAN;
    }

    public BattleActor getFirstLivingAlly() {
        for (BattleActor ally : allies) {
            if (ally.isAlive()) {
                return ally;
            }
        }

        return null;
    }

    public void setBattleMessage(String battleMessage) {
        this.battleMessage = battleMessage;
    }

    public Library.BattleResult handleAttack(BattleActor target) {
        BattleActor attacker = getFirstLivingAlly();

        if (attacker == null) {
            battleMessage = "No allies can act.";
            return Library.BattleResult.DEFEAT;
        }

        if (!canTarget(attacker, target, Library.BattleTargetingMode.NORMAL_MELEE)) {
            battleMessage = "You cannot reach " + target.getName() + ".";
            return Library.BattleResult.CONTINUE;
        }

        int damage = attacker.getAttackDamage();
        target.takeDamage(damage);
        playSound(attacker.getAttackSoundPath());
        playSound(target.getHitSoundPath());

        battleMessage = attacker.getName()
                + " attacks "
                + target.getName()
                + " for "
                + damage
                + " damage!";

        if (allEnemiesDefeated()) {
            battleMessage = "Victory!";
            return Library.BattleResult.VICTORY;
        }

        return handleEnemyTurn();
    }

    private Library.BattleResult handleEnemyTurn() {
        StringBuilder turnSummary = new StringBuilder(battleMessage);

        for (BattleActor attacker : enemies) {
            if (!attacker.isAlive()) {
                continue;
            }

            if (attacker.isStunned()) {
                turnSummary
                        .append(" ")
                        .append(attacker.getName())
                        .append(" is stunned!");
                attacker.tickStatusDurations();
                continue;
            }

            BattleActor target = getFirstLivingAlly();

            if (target == null) {
                battleMessage = "Defeat!";
                return Library.BattleResult.DEFEAT;
            }

            BattleSkill selectedSkill = chooseEnemySkill(attacker);

            if (selectedSkill != null) {
                String skillSummary = resolveEnemySkill(attacker, selectedSkill);
                turnSummary.append(" ").append(skillSummary);

                if (getFirstLivingAlly() == null) {
                    battleMessage = "Defeat!";
                    return Library.BattleResult.DEFEAT;
                }

                attacker.tickStatusDurations();
                continue;
            }

            int damage = attacker.getAttackDamage();
            playSound(attacker.getAttackSoundPath());
            target.takeDamage(damage);
            playSound(target.getHitSoundPath());

            turnSummary
                    .append(" ")
                    .append(attacker.getName())
                    .append(" hits ")
                    .append(target.getName())
                    .append(" for ")
                    .append(damage)
                    .append(" damage!");

            if (!target.isAlive()) {
                battleMessage = "Defeat!";
                return Library.BattleResult.DEFEAT;
            }

            attacker.tickStatusDurations();
        }

        allies.forEach(BattleActor::tickStatusDurations);
        battleMessage = turnSummary.toString();
        return Library.BattleResult.CONTINUE;
    }

    private BattleSkill chooseEnemySkill(BattleActor attacker) {
        if (attacker == null || attacker.getIntelligence() <= 0 || attacker.getSkills().isEmpty()) {
            return null;
        }

        double skillChance = attacker.getIntelligence() / 10.0;

        if (Math.random() > skillChance) {
            return null;
        }

        List<BattleSkill> usableSkills = attacker.getSkills().stream()
                .filter(skill -> !getSelectableActorsForSkill(attacker, skill).isEmpty())
                .toList();

        if (usableSkills.isEmpty()) {
            return null;
        }

        BattleSkill drainSkill = usableSkills.stream()
                .filter(BattleSkill::healsCasterFromDamage)
                .findFirst()
                .orElse(null);

        if (drainSkill != null && attacker.getCurrentHp() < attacker.getMaxHp()) {
            return drainSkill;
        }

        return usableSkills.getFirst();
    }

    private String resolveEnemySkill(BattleActor attacker, BattleSkill skill) {
        List<BattleActor> targets = getSelectableActorsForSkill(attacker, skill);

        if (targets.isEmpty()) {
            return attacker.getName() + " hesitates.";
        }

        BattleActor target = selectEnemySkillTarget(attacker, skill, targets);
        List<BattleActor> resolvedTargets = resolveSkillTargets(attacker, target, skill);

        if (resolvedTargets.isEmpty()) {
            return attacker.getName() + " hesitates.";
        }

        int totalDamage = 0;

        for (BattleActor resolvedTarget : resolvedTargets) {
            if (skill.getEffectType().equals(Library.EffectType.DAMAGE)) {
                totalDamage += resolvedTarget.takeDamage(skill.getDamage());
                playSound(resolvedTarget.getHitSoundPath());
            } else if (skill.getEffectType().equals(Library.EffectType.HEAL)) {
                resolvedTarget.healDamage(skill.getDamage());
            } else if (skill.getEffectType().equals(Library.EffectType.DEFEND)) {
                resolvedTarget.applyDefend(skill.getDefendTurns(), skill.getDamageReduction());
            }
        }

        if (skill.healsCasterFromDamage() && totalDamage > 0) {
            attacker.healDamage((int) Math.ceil(totalDamage * skill.getSelfHealPercent()));
        }

        playSound(skill.getUseSoundPath());
        return attacker.getName()
                + " uses "
                + skill.getName()
                + " on "
                + joinActorNames(resolvedTargets)
                + "!";
    }

    private BattleActor selectEnemySkillTarget(BattleActor attacker, BattleSkill skill, List<BattleActor> targets) {
        if (attacker.getIntelligence() >= 7 && skill.getEffectType() == Library.EffectType.DAMAGE) {
            return targets.stream()
                    .min(Comparator.comparingInt(BattleActor::getCurrentHp))
                    .orElse(targets.getFirst());
        }

        return targets.getFirst();
    }

    private BattleActor getFirstLivingEnemy() {
        for (BattleActor enemy : enemies) {
            if (enemy.isAlive()) {
                return enemy;
            }
        }

        return null;
    }

    private boolean allEnemiesDefeated() {
        for (BattleActor enemy : enemies) {
            if (enemy.isAlive()) {
                return false;
            }
        }

        return true;
    }

    public List<BattleActor> getAllies() {
        return allies;
    }

    public List<BattleActor> getEnemies() {
        return enemies;
    }

    public String getBattleMessage() {
        return battleMessage;
    }

    public int getDefeatedEnemyExperienceReward() {
        int reward = 0;

        for (BattleActor enemy : enemies) {
            if (!enemy.isAlive()) {
                reward += enemy.getExperienceReward();
            }
        }

        return reward;
    }

    private void playSound(String soundPath) {
        if (soundSystem != null) {
            soundSystem.playSound(soundPath);
        }
    }
}
