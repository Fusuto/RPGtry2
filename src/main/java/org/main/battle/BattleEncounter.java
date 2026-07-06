package org.main.battle;

import org.main.engine.EntityType;
import org.main.monsters.Monster;

import java.util.ArrayList;
import java.util.List;

public class BattleEncounter {
    private final List<BattleActor> allies;
    private final List<BattleActor> enemies;

    private String battleMessage = "Battle begins!";

    public BattleEncounter(List<BattleActor> allies, List<BattleActor> enemies) {
        this.allies = allies;
        this.enemies = enemies;

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
            BattleRow row = i < 3 ? BattleRow.FRONT : BattleRow.BACK;

            actor.setBattlePosition(row, slot);
        }
    }

    public static BattleEncounter fromMonster(List<Monster> monster) {
        BattleActor playerActor = new BattleActor(
                "Player",
                30,
                30,
                null,
                false,
                EntityType.ALLY
        );

        playerActor.addSkill(new BattleSkill(
                "Fireball",
                "Hits every enemy.",
                SkillTargetShape.ENTIRE_SIDE,
                EntityType.ENEMY,
                BattleTargetingMode.MAGIC
        ));

        playerActor.addSkill(new BattleSkill(
                "Piercing Line",
                "Hits one horizontal lane.",
                SkillTargetShape.SINGLE_ROW,
                EntityType.ENEMY,
                BattleTargetingMode.RANGED
        ));

        playerActor.addSkill(new BattleSkill(
                "Crush Column",
                "Hits either the front or back column.",
                SkillTargetShape.SINGLE_COLUMN,
                EntityType.ENEMY,
                BattleTargetingMode.MAGIC
        ));

        playerActor.addSkill(new BattleSkill(
                "Heal",
                "Targets one ally.",
                SkillTargetShape.SINGLE_TARGET,
                EntityType.ALLY,
                BattleTargetingMode.MAGIC
        ));

        List<BattleActor> monsterActors = new ArrayList<>();

        monster.forEach(monster1 -> {
            var enemy = new BattleActor(
                    monster1.getName(),
                    monster1.getMaxHp(),
                    monster1.getCurrentHp(),
                    monster1.getType().getImg(),
                    true,
                    EntityType.ENEMY
            );
            monsterActors.add(enemy);
        });

        return new BattleEncounter(
                List.of(playerActor),
                monsterActors
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
        if (skill.getTargetTeam() == EntityType.ALLY) {
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

            boolean shouldInclude = BattleRenderer.matchesSkillShape(actor, selectedActor, skill.getTargetShape());

            if (shouldInclude) {
                resolvedTargets.add(actor);
            }
        }

        return resolvedTargets;
    }

    private List<BattleActor> getActorsForSkillTargetTeam(
            BattleActor caster,
            EntityType targetTeam
    ) {
        if (targetTeam == EntityType.ALLY) {
            return getActorsOnSameSide(caster);
        }

        return getOpposingActors(caster);
    }

    public BattleResult handleSkill(
            BattleActor caster,
            BattleSkill skill,
            List<BattleActor> targets
    ) {
        if (caster == null || skill == null) {
            battleMessage = "No skill selected.";
            return BattleResult.CONTINUE;
        }

        if (targets == null || targets.isEmpty()) {
            battleMessage = "No valid targets.";
            return BattleResult.CONTINUE;
        }

        battleMessage = caster.getName()
                + " uses "
                + skill.getName()
                + " on "
                + joinActorNames(targets)
                + ".";

        return BattleResult.CONTINUE;
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

    public BattleResult handleRunCommand(BattleCommand command) {
            return handleRun();
    }

    public List<BattleActor> getValidTargets(
            BattleActor attacker,
            BattleTargetingMode targetingMode
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
            BattleTargetingMode targetingMode
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
        if (target.getRow() != BattleRow.BACK) {
            return false;
        }

        List<BattleActor> sameSideActors = getActorsOnSameSide(target);

        for (BattleActor actor : sameSideActors) {
            if (!actor.isAlive()) {
                continue;
            }

            boolean sameSlot = actor.getSlot() == target.getSlot();
            boolean isFrontRow = actor.getRow() == BattleRow.FRONT;

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

    private BattleResult handleRun() {
        battleMessage = "You ran away.";
        return BattleResult.RAN;
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

    public BattleResult handleAttack(BattleActor target) {
        BattleActor attacker = getFirstLivingAlly();

        if (attacker == null) {
            battleMessage = "No allies can act.";
            return BattleResult.DEFEAT;
        }

        if (!canTarget(attacker, target, BattleTargetingMode.NORMAL_MELEE)) {
            battleMessage = "You cannot reach " + target.getName() + ".";
            return BattleResult.CONTINUE;
        }

        int damage = 5;
        target.takeDamage(damage);

        battleMessage = attacker.getName()
                + " attacks "
                + target.getName()
                + " for "
                + damage
                + " damage!";

        if (allEnemiesDefeated()) {
            battleMessage = "Victory!";
            return BattleResult.VICTORY;
        }

        return BattleResult.CONTINUE;
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
}