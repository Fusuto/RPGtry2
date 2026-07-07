package org.main.battle;

import org.main.content.EnvironmentLibrary;
import org.main.content.SkillLibrary;
import org.main.core.InventorySystem;
import org.main.core.Library;
import org.main.engine.SoundSystem;
import org.main.monsters.Monster;

import java.util.ArrayList;
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
        return fromMonster(monster, null, null, null);
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
        BattleActor playerActor = new BattleActor(
                "Player",
                30,
                30,
                null,
                Library.EntityType.ALLY,
                5
        );
        playerActor.setHitSoundPath(environment == null ? null : environment.getPlayerHitSoundPath());

        for (BattleSkill skill : SkillLibrary.createDefaultPlayerSkills()) {
            playerActor.addSkill(skill);
        }

        if (inventory != null) {
            InventorySystem.Item weapon = inventory.getEquippedItem(InventorySystem.EquipmentSlot.WEAPON);

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

        targets.forEach(target -> {
            if (skill.getEffectType().equals(Library.EffectType.DAMAGE)) {
                target.takeDamage(skill.getDamage());
                playSound(target.getHitSoundPath());
            } else if (skill.getEffectType().equals(Library.EffectType.HEAL)) {
                target.healDamage(skill.getDamage());
            }
        });

        playSound(skill.getUseSoundPath());

        return Library.BattleResult.CONTINUE;
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

        return handleEnemyAttack();
    }

    private Library.BattleResult handleEnemyAttack() {
        BattleActor attacker = getFirstLivingEnemy();
        BattleActor target = getFirstLivingAlly();

        if (attacker == null) {
            return Library.BattleResult.CONTINUE;
        }

        if (target == null) {
            return Library.BattleResult.DEFEAT;
        }

        int damage = attacker.getAttackDamage();
        playSound(attacker.getAttackSoundPath());
        target.takeDamage(damage);
        playSound(target.getHitSoundPath());

        battleMessage = battleMessage
                + " "
                + attacker.getName()
                + " hits "
                + target.getName()
                + " for "
                + damage
                + " damage!";

        if (!target.isAlive()) {
            battleMessage = "Defeat!";
            return Library.BattleResult.DEFEAT;
        }

        return Library.BattleResult.CONTINUE;
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

    private void playSound(String soundPath) {
        if (soundSystem != null) {
            soundSystem.playSound(soundPath);
        }
    }
}
