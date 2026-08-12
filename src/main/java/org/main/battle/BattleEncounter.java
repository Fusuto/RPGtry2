package org.main.battle;

import org.main.core.GameEnvironment;
import org.main.content.MapDesignLibrary;
import org.main.content.CharacterModelDefinition;
import org.main.content.BattleContentTypeRegistry;
import org.main.content.BattleContentCatalog;
import org.main.content.SkillEffectDefinition;
import org.main.content.StatusDefinition;
import org.main.core.GameBootstrap;
import org.main.core.CharacterSkill;
import org.main.core.GameConfiguration;
import org.main.core.InventorySystem;
import org.main.core.Library;
import org.main.core.PaperDollRenderer;
import org.main.core.PlayerCharacter;
import org.main.core.PlayerStat;
import org.main.core.PartyFormation;
import org.main.engine.SoundSystem;
import org.main.experimental.FirstPersonAnimationRuntime;
import org.main.monsters.Monster;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class BattleEncounter {
    private static final int FRONT_ROW_ACTOR_COUNT = 3;
    private static final int BATTLE_SLOT_COUNT = 3;
    private static final int BASE_PLAYER_ATTACK_DAMAGE = 5;

    private final List<BattleActor> allies;
    private final List<BattleActor> enemies;
    private final SoundSystem soundSystem;
    private final BattlePresentationDirector presentationDirector = new BattlePresentationDirector();

    private String battleMessage = "Battle begins!";
    private Library.BattleResult pendingImpactResult = Library.BattleResult.CONTINUE;
    private final Set<BattleActor> scheduledDeathPresentations = new HashSet<>();

    public BattleEncounter(List<BattleActor> allies, List<BattleActor> enemies) {
        this(allies, enemies, null);
    }

    public SandboxResult executeSkillImmediatelyForSandbox(
            BattleActor caster,
            BattleSkill skill,
            List<BattleActor> targets,
            long seed
    ) {
        int casterBefore = caster == null ? 0 : caster.getCurrentHp();
        List<Integer> targetBefore = targets == null ? List.of()
                : targets.stream().map(BattleActor::getCurrentHp).toList();
        try (BattleRandom.Scope ignored = BattleRandom.withSeed(seed)) {
            BattleActionIntent.OutcomePlan plan = planSkillOutcome(
                    caster, skill, targets == null ? List.of() : targets, false);
            if (plan == null) {
                return new SandboxResult(
                        "Skill could not be executed with the selected profiles.",
                        casterBefore,
                        casterBefore,
                        targetBefore,
                        targetBefore,
                        List.of());
            }
            plan.impactCommit().run();
        }
        List<BattleActor> safeTargets = targets == null ? List.of() : targets;
        return new SandboxResult(
                battleMessage,
                casterBefore,
                caster == null ? 0 : caster.getCurrentHp(),
                targetBefore,
                safeTargets.stream().map(BattleActor::getCurrentHp).toList(),
                safeTargets.stream()
                        .flatMap(actor -> actor.getStatuses().stream())
                        .map(status -> status.getDefinition().displayName()
                                + " (" + status.getRemainingTurns() + " turns)")
                        .toList());
    }

    public record SandboxResult(
            String message,
            int casterHpBefore,
            int casterHpAfter,
            List<Integer> targetHpBefore,
            List<Integer> targetHpAfter,
            List<String> statuses
    ) {
    }

    public BattleEncounter(List<BattleActor> allies, List<BattleActor> enemies, SoundSystem soundSystem) {
        this.allies = allies == null ? new ArrayList<>() : new ArrayList<>(allies);
        this.enemies = enemies == null ? new ArrayList<>() : new ArrayList<>(enemies);
        this.soundSystem = soundSystem;

        assignFormations();
        compactBattleLanes(allies);
        compactBattleLanes(enemies);
        resetAllAttackCooldowns();
    }

    public BattlePresentationDirector getPresentationDirector() {
        return presentationDirector;
    }

    private void assignFormations() {
        assignAlliedFormation();
        assignFormation(enemies);
    }

    private void assignAlliedFormation() {
        boolean[] occupied = new boolean[6];
        for (BattleActor actor : allies) {
            PlayerCharacter source = actor.getFormationOwner();
            PartyFormation.Cell cell = source == null
                    ? null
                    : source.getPartyFormation().cellOf(actor.getPartyMemberId());
            if (cell == null) {
                continue;
            }
            actor.setBattlePosition(cell.row(), cell.column());
            occupied[(cell.row() == Library.BattleRow.FRONT ? 0 : 3) + cell.column()] = true;
        }
        for (BattleActor actor : allies) {
            if (actor.getFormationOwner() != null || !actor.isAlive() || !actor.hasBattlePositionAssignment()) continue;
            int index = (actor.getRow() == Library.BattleRow.FRONT ? 0 : 3) + actor.getSlot();
            boolean supported = index < 3 || occupied[index - 3];
            if (index >= 0 && index < occupied.length && !occupied[index] && supported) {
                occupied[index] = true;
            } else {
                actor.clearBattlePositionAssignment();
            }
        }
        for (BattleActor actor : allies) {
            if (actor.getFormationOwner() != null || !actor.isAlive() || actor.hasBattlePositionAssignment()) {
                continue;
            }
            int index = firstLegalFormationIndex(occupied);
            if (index < 0) {
                break;
            }
            occupied[index] = true;
            actor.setBattlePosition(index < 3 ? Library.BattleRow.FRONT : Library.BattleRow.BACK, index % 3);
        }
    }

    private static int firstLegalFormationIndex(boolean[] occupied) {
        int[] preferred = {1, 0, 2, 4, 3, 5};
        for (int index : preferred) {
            boolean supported = index < 3 || occupied[index - 3];
            if (!occupied[index] && supported) {
                return index;
            }
        }
        return -1;
    }

    private void assignFormation(List<BattleActor> actors) {
        for (int i = 0; i < actors.size(); i++) {
            BattleActor actor = actors.get(i);

            int slot = i % BATTLE_SLOT_COUNT;
            Library.BattleRow row = i < FRONT_ROW_ACTOR_COUNT ? Library.BattleRow.FRONT : Library.BattleRow.BACK;

            actor.setBattlePosition(row, slot);
        }
    }

    /**
     * Removes empty lateral lanes once, when the encounter is created. Actor
     * order and front/back rows are preserved, and persistent party formation
     * data is never modified because BattleActors are encounter-local copies.
     */
    private static void compactBattleLanes(List<BattleActor> actors) {
        if (actors == null || actors.isEmpty()) {
            return;
        }
        boolean[] occupiedLanes = new boolean[BATTLE_SLOT_COUNT];
        for (BattleActor actor : actors) {
            if (actor != null && actor.hasBattlePositionAssignment()
                    && actor.getSlot() >= 0 && actor.getSlot() < BATTLE_SLOT_COUNT) {
                occupiedLanes[actor.getSlot()] = true;
            }
        }

        int[] compactedLane = new int[BATTLE_SLOT_COUNT];
        int nextLane = 0;
        for (int lane = 0; lane < BATTLE_SLOT_COUNT; lane++) {
            compactedLane[lane] = occupiedLanes[lane] ? nextLane++ : -1;
        }
        for (BattleActor actor : actors) {
            if (actor == null || !actor.hasBattlePositionAssignment()
                    || actor.getSlot() < 0 || actor.getSlot() >= BATTLE_SLOT_COUNT) {
                continue;
            }
            int lane = compactedLane[actor.getSlot()];
            if (lane >= 0 && lane != actor.getSlot()) {
                actor.setBattlePosition(actor.getRow(), lane);
            }
        }
    }

    private static boolean assignSummonedActorToOpenPosition(
            BattleActor summoned,
            List<BattleActor> existingActors
    ) {
        if (summoned == null) {
            return false;
        }
        boolean[] occupied = new boolean[FRONT_ROW_ACTOR_COUNT * 2];
        if (existingActors != null) {
            for (BattleActor actor : existingActors) {
                if (actor == null || !actor.hasBattlePositionAssignment()) {
                    continue;
                }
                int index = (actor.getRow() == Library.BattleRow.FRONT ? 0 : FRONT_ROW_ACTOR_COUNT)
                        + actor.getSlot();
                if (index >= 0 && index < occupied.length) {
                    occupied[index] = true;
                }
            }
        }
        int index = firstLegalFormationIndex(occupied);
        if (index < 0) {
            return false;
        }
        summoned.setBattlePosition(
                index < FRONT_ROW_ACTOR_COUNT ? Library.BattleRow.FRONT : Library.BattleRow.BACK,
                index % BATTLE_SLOT_COUNT);
        return true;
    }

    private void resetAllAttackCooldowns() {
        for (BattleActor actor : allActors()) {
            actor.resetAttackCooldown();
        }
    }

    private List<BattleActor> allActors() {
        List<BattleActor> actors = new ArrayList<>();
        actors.addAll(allies);
        actors.addAll(enemies);
        return actors;
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
            GameEnvironment environment
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
            GameEnvironment environment
    ) {
        if (playerCharacter == null) {
            playerCharacter = GameBootstrap.createDefaultPlayerCharacter();
        }

        int equipmentAttackBonus = playerCharacter.getInventory() == null ? 0 : playerCharacter.getUsableWeaponStatBonus();
        int equipmentDefenseBonus = playerCharacter.getInventory() == null ? 0 : playerCharacter.getUsableArmorStatBonus();
        BufferedImage playerBattleImage = new PaperDollRenderer().render(playerCharacter);

        BattleActor playerActor = new BattleActor(
                playerCharacter.getName(),
                playerCharacter.getMaxHp(),
                playerCharacter.getCurrHp(),
                playerBattleImage,
                Library.EntityType.ALLY,
                BASE_PLAYER_ATTACK_DAMAGE + playerCharacter.getCombinedStat(PlayerStat.STRENGTH) + equipmentAttackBonus,
                playerCharacter.getCombinedStat(PlayerStat.DEFENSE) + equipmentDefenseBonus
        );
        playerActor.copyCombatProfileFrom(playerCharacter);
        playerActor.setHitSoundPath(environment == null ? null : environment.getPlayerHitSoundPath());
        playerActor.setAttackSoundPath(GameConfiguration.stringValue("battle.playerAutoAttack.soundPath", ""));

        for (BattleSkill skill : playerCharacter.getBattleSkills()) {
            playerActor.addSkill(skill);
        }

        if (playerCharacter.getInventory() != null) {
            InventorySystem.Item weapon = playerCharacter.getInventory().getEquippedItem(InventorySystem.EquipmentSlot.WEAPON);

            if (weapon != null && weapon.getUseSoundPath() != null && !weapon.getUseSoundPath().isBlank()) {
                playerActor.setAttackSoundPath(weapon.getUseSoundPath());
            }
        }

        List<BattleActor> monsterActors = new ArrayList<>();

        monster.forEach(monster1 -> monsterActors.add(createEnemyActor(monster1)));

        return new BattleEncounter(
                List.of(playerActor),
                monsterActors,
                soundSystem
        );
    }

    private static BattleActor createEnemyActor(Monster monster) {
        return createMonsterActor(monster, Library.EntityType.ENEMY);
    }

    private static BattleActor createMonsterActor(Monster monster, Library.EntityType entityType) {
        var enemy = new BattleActor(
                monster.getName(),
                monster.getMaxHp(),
                monster.getCurrentHp(),
                monster.getImage(),
                entityType,
                monster.getStat(PlayerStat.STRENGTH),
                monster.getStat(PlayerStat.DEFENSE)
        );
        enemy.setAttackSoundPath(monster.getAttackSoundPath());
        enemy.setHitSoundPath(monster.getDamageSoundPath());
        enemy.configureMonsterCombatStats(monster.getStatsView());
        enemy.setCombatAiIntelligence(monster.getCombatAiIntelligence());
        enemy.setSpeciesId(monster.getCustomId());
        enemy.setExperienceReward(monster.getXpReward());
        enemy.setCharacterModel(monster.getCharacterModel());
        monster.getSkillIds().forEach(skillId -> enemy.addSkill(BattleContentCatalog.createSkill(skillId)));
        return enemy;
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

        if (!caster.isSkillReady(skill)) {
            battleMessage = skill.getName() + " is not ready.";
            return Library.BattleResult.CONTINUE;
        }

        if (!skill.isSummonSkill() && (targets == null || targets.isEmpty())) {
            battleMessage = "No valid targets.";
            return Library.BattleResult.CONTINUE;
        }
        List<BattleActor> safeTargets = skill.isSummonSkill() ? List.of() : List.copyOf(targets);
        BattlePresentationDirector.ActionType presentationType = presentationTypeFor(skill);
        FirstPersonAnimationRuntime.PresentationTiming timing =
                FirstPersonAnimationRuntime.timing(caster, presentationType);
        double impactFraction = timing.durationMs() > 0
                ? timing.impactFraction() : impactFractionFor(caster, skill);
        BattleActionIntent intent = new BattleActionIntent(
                caster,
                skill.getName(),
                presentationType,
                BattleActionIntent.Priority.MANUAL,
                impactFraction,
                timing.durationMs(),
                safeTargets,
                () -> planSkillOutcome(caster, skill, safeTargets, false)
        );
        if (!presentationDirector.enqueue(intent)) {
            battleMessage = caster.getName() + " is already committed to another action.";
            return Library.BattleResult.CONTINUE;
        }
        battleMessage = caster.getName() + " prepares " + skill.getName() + ".";
        return Library.BattleResult.CONTINUE;
    }

    private BattleActionIntent.OutcomePlan planSkillOutcome(
            BattleActor caster,
            BattleSkill skill,
            List<BattleActor> targets,
            boolean enemyControlled
    ) {
        if (caster == null || skill == null || !caster.isAlive() || !caster.isSkillReady(skill)) {
            return null;
        }
        if (skill.isSummonSkill()
                && !skill.hasEffect("damage")
                && !skill.hasEffect("heal")
                && !skill.hasEffect("apply_status")) {
            if (!canSummon(caster)) {
                return null;
            }
            startManualSkillCost(caster, skill);
            if (enemyControlled) {
                caster.resetAttackCooldown();
                caster.tickStatusDurations();
            }
            return new BattleActionIntent.OutcomePlan(List.of(), () -> {
                battleMessage = caster.getName() + " uses " + skill.getName() + ".";
                appendBattleMessage(resolveSummon(caster, skill));
                playSound(skill.getUseSoundPath());
            });
        }
        List<PlannedSkillOutcome> outcomes = new ArrayList<>();
        List<BattlePresentationDirector.TargetReaction> reactions = new ArrayList<>();
        for (BattleActor target : targets) {
            if (target == null || !target.isAlive()) continue;
            SkillEffectDefinition primaryDamage = firstEffectForResolvedTargets(skill, "damage");
            SkillEffectDefinition primaryHeal = firstEffectForResolvedTargets(skill, "heal");
            if (primaryDamage != null) {
                BattleSkill effectSkill = skill.effectView(
                        "damage", primaryDamage.intParameter("potency", skill.getPrimaryPotency()));
                boolean activates = primaryDamage.condition() == SkillEffectDefinition.ActivationCondition.ALWAYS
                        && (primaryDamage.chance() >= 1.0
                        || BattleRandom.nextDouble() < primaryDamage.chance());
                CombatResolver.CombatResult result = activates
                        ? (skill.getTargetingMode() == Library.BattleTargetingMode.MAGIC
                                ? CombatResolver.resolveSpell(caster, target, effectSkill)
                                : CombatResolver.resolvePhysicalSkill(caster, target, effectSkill))
                        : new CombatResolver.CombatResult(false, 0, 0, 0, "fails to activate");
                int projectedDamage = Math.max(0, result.damage());
                outcomes.add(new PlannedSkillOutcome(target, result, projectedDamage, 0));
                reactions.add(new BattlePresentationDirector.TargetReaction(
                        target, reactionFor(result.hit(), projectedDamage, target), projectedDamage));
            } else if (primaryHeal != null) {
                boolean activates = primaryHeal.condition() == SkillEffectDefinition.ActivationCondition.ALWAYS
                        && (primaryHeal.chance() >= 1.0
                        || BattleRandom.nextDouble() < primaryHeal.chance());
                BattleSkill effectSkill = skill.effectView(
                        "heal", primaryHeal.intParameter("potency", skill.getPrimaryPotency()));
                int heal = activates ? CombatResolver.resolveHealingAmount(caster, effectSkill) : 0;
                outcomes.add(new PlannedSkillOutcome(target, null, 0, heal));
                reactions.add(new BattlePresentationDirector.TargetReaction(target,
                        BattlePresentationDirector.Reaction.NONE, 0));
            } else {
                outcomes.add(new PlannedSkillOutcome(target, null, 0, 0));
                reactions.add(new BattlePresentationDirector.TargetReaction(target,
                        BattlePresentationDirector.Reaction.BLOCK, 0));
            }
        }
        if (outcomes.isEmpty()) return null;
        startManualSkillCost(caster, skill);
        if (enemyControlled) {
            caster.resetAttackCooldown();
            caster.tickStatusDurations();
        }
        return new BattleActionIntent.OutcomePlan(reactions,
                () -> commitSkillOutcome(caster, skill, outcomes, enemyControlled));
    }

    private void commitSkillOutcome(
            BattleActor caster,
            BattleSkill skill,
            List<PlannedSkillOutcome> outcomes,
            boolean enemyControlled
    ) {
        int totalDamage = 0;
        boolean anyHit = false;
        battleMessage = caster.getName() + " uses " + skill.getName() + " on "
                + joinActorNames(outcomes.stream().map(PlannedSkillOutcome::target).toList()) + ".";
        for (PlannedSkillOutcome outcome : outcomes) {
            BattleActor target = outcome.target();
            if (!target.isAlive() && outcome.combatResult() != null) continue;
            if (outcome.combatResult() != null) {
                CombatResolver.CombatResult result = outcome.combatResult();
                int damage = target.takeDamage(outcome.projectedDamage());
                totalDamage += damage;
                anyHit |= result.hit();
                appendBattleMessage(target.getName() + " " + result.text() + ".");
                awardOffensiveSkillExperience(caster, skill, result, damage);
                target.addCombatSkillExperience(CharacterSkill.DEFENSE,
                        Math.max(defenseXpMinimum(), damage * defenseXpPerDamage()));
                if (result.hit()) {
                    playSound(target.getHitSoundPath());
                }
            } else if (hasEffectForResolvedTargets(skill, "heal")) {
                int before = target.getCurrentHp();
                target.healDamage(outcome.healAmount());
                int healed = target.getCurrentHp() - before;
                caster.addCombatSkillExperience(CharacterSkill.MAGIC_POWER,
                        Math.max(magicHealingXpMinimum(), healed * magicHealingXpPerHp()));
                appendBattleMessage(target.getName() + " recovers " + healed + " HP.");
            }
            SupplementalEffectResult supplemental = applyAuthoredEffects(
                    caster,
                    target,
                    skill,
                    SkillEffectDefinition.RecipientScope.RESOLVED_TARGETS,
                    outcome.combatResult() != null && outcome.combatResult().hit(),
                    outcome.projectedDamage(),
                    totalDamage,
                    true);
            totalDamage += supplemental.damageDealt();
            anyHit |= supplemental.anyHit();
        }
        applyAuthoredEffects(
                caster,
                caster,
                skill,
                SkillEffectDefinition.RecipientScope.CASTER,
                anyHit,
                totalDamage,
                totalDamage,
                false);
        if (skill.isSummonSkill() && (skill.hasEffect("damage")
                || skill.hasEffect("heal") || skill.hasEffect("apply_status"))) {
            appendBattleMessage(resolveSummon(caster, skill));
        }
        playSound(skill.getUseSoundPath());
        updatePendingBattleResult();
    }

    private SupplementalEffectResult applyAuthoredEffects(
            BattleActor caster,
            BattleActor recipient,
            BattleSkill skill,
            SkillEffectDefinition.RecipientScope passScope,
            boolean initialHit,
            int initialDamage,
            int anySkillDamage,
            boolean skipPrimary
    ) {
        boolean previousHit = initialHit;
        int previousDamage = Math.max(0, initialDamage);
        boolean anyHit = initialHit;
        int additionalDamage = 0;
        boolean skippedDamage = false;
        boolean skippedHeal = false;

        for (SkillEffectDefinition effect : skill.getEffects()) {
            boolean appliesToPass = effect.recipientScope() == passScope
                    || effect.recipientScope() == SkillEffectDefinition.RecipientScope.BOTH;
            if (!appliesToPass) {
                continue;
            }
            if (skipPrimary && "damage".equals(effect.kindId()) && !skippedDamage) {
                skippedDamage = true;
                continue;
            }
            if (skipPrimary && "heal".equals(effect.kindId()) && !skippedHeal) {
                skippedHeal = true;
                continue;
            }
            if (!effectConditionMatches(
                    effect.condition(), previousHit, previousDamage, anySkillDamage + additionalDamage, recipient)) {
                previousHit = false;
                previousDamage = 0;
                continue;
            }
            if (effect.chance() < 1.0 && BattleRandom.nextDouble() >= effect.chance()) {
                previousHit = false;
                previousDamage = 0;
                continue;
            }

            switch (effect.kindId()) {
                case "damage" -> {
                    BattleSkill effectSkill = skill.effectView("damage", effect.intParameter("potency", 0));
                    CombatResolver.CombatResult result = effectSkill.getTargetingMode() == Library.BattleTargetingMode.MAGIC
                            ? CombatResolver.resolveSpell(caster, recipient, effectSkill)
                            : CombatResolver.resolvePhysicalSkill(caster, recipient, effectSkill);
                    int damage = result.hit() ? recipient.takeDamage(result.damage()) : 0;
                    previousHit = result.hit();
                    previousDamage = damage;
                    anyHit |= result.hit();
                    additionalDamage += damage;
                    appendBattleMessage(recipient.getName() + " " + result.text() + ".");
                }
                case "heal" -> {
                    BattleSkill effectSkill = skill.effectView("heal", effect.intParameter("potency", 0));
                    int before = recipient.getCurrentHp();
                    recipient.healDamage(CombatResolver.resolveHealingAmount(caster, effectSkill));
                    int healed = recipient.getCurrentHp() - before;
                    previousHit = true;
                    previousDamage = 0;
                    appendBattleMessage(recipient.getName() + " recovers " + healed + " HP.");
                }
                case "heal_from_damage" -> {
                    int baseDamage = Math.max(0, anySkillDamage + additionalDamage);
                    int healed = (int) Math.ceil(baseDamage
                            * Math.max(0, effect.doubleParameter("percent", 0)) / 100.0);
                    int before = recipient.getCurrentHp();
                    recipient.healDamage(healed);
                    appendBattleMessage(recipient.getName() + " recovers "
                            + (recipient.getCurrentHp() - before) + " HP.");
                    previousHit = true;
                    previousDamage = 0;
                }
                case "apply_status" -> {
                    String statusId = BattleContentCatalog.normalizeId(effect.parameter("statusId", ""));
                    StatusDefinition statusDefinition = BattleContentCatalog.findStatus(statusId);
                    if (statusDefinition != null) {
                        int duration = effect.intParameter("duration", statusDefinition.defaultDuration());
                        int magnitude = effect.intParameter("magnitude", 0);
                        if (magnitude > 0 && statusDefinition.intParameter("magnitude", 0) < 0) {
                            magnitude = -magnitude;
                        }
                        recipient.applyStatus(statusId, duration, magnitude);
                        appendBattleMessage(recipient.getName() + " is affected by "
                                + statusDefinition.displayName() + ".");
                        previousHit = true;
                    } else {
                        previousHit = false;
                    }
                    previousDamage = 0;
                }
                case "remove_status" -> {
                    String statusId = effect.parameter("statusId", "");
                    StatusDefinition.Polarity polarity = parsePolarity(effect.parameter("polarity", "HARMFUL"));
                    int removed = recipient.removeStatuses(
                            statusId, polarity, effect.intParameter("count", 1));
                    appendBattleMessage(recipient.getName() + " loses " + removed + " status effect(s).");
                    previousHit = removed > 0;
                    previousDamage = 0;
                }
                case "set_hp_percent" -> {
                    int hp = (int) Math.ceil(recipient.getMaxHp()
                            * Math.max(0, Math.min(100, effect.doubleParameter("percent", 10))) / 100.0);
                    recipient.setCurrentHp(hp);
                    previousHit = true;
                    previousDamage = 0;
                }
                case "summon", "no_op" -> {
                    previousHit = true;
                    previousDamage = 0;
                }
                default -> {
                    previousHit = false;
                    previousDamage = 0;
                }
            }
        }
        return new SupplementalEffectResult(additionalDamage, anyHit);
    }

    private boolean effectConditionMatches(
            SkillEffectDefinition.ActivationCondition condition,
            boolean previousHit,
            int previousDamage,
            int anyDamage,
            BattleActor recipient
    ) {
        return switch (condition) {
            case ALWAYS -> true;
            case PREVIOUS_EFFECT_HIT -> previousHit;
            case PREVIOUS_EFFECT_DEALT_DAMAGE -> previousDamage > 0;
            case ANY_SKILL_DAMAGE_DEALT -> anyDamage > 0;
            case TARGET_DEFEATED -> recipient != null && !recipient.isAlive();
        };
    }

    private StatusDefinition.Polarity parsePolarity(String value) {
        if (value == null || value.isBlank() || "ANY".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return StatusDefinition.Polarity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record SupplementalEffectResult(int damageDealt, boolean anyHit) {
    }

    private record PlannedSkillOutcome(
            BattleActor target,
            CombatResolver.CombatResult combatResult,
            int projectedDamage,
            int healAmount
    ) { }

    private boolean hasEffectForResolvedTargets(BattleSkill skill, String kindId) {
        return skill != null && skill.getEffects().stream()
                .anyMatch(effect -> kindId.equals(effect.kindId())
                        && (effect.recipientScope() == SkillEffectDefinition.RecipientScope.RESOLVED_TARGETS
                        || effect.recipientScope() == SkillEffectDefinition.RecipientScope.BOTH));
    }

    private SkillEffectDefinition firstEffectForResolvedTargets(BattleSkill skill, String kindId) {
        return skill == null ? null : skill.getEffects().stream()
                .filter(effect -> kindId.equals(effect.kindId()))
                .filter(effect -> effect.recipientScope() == SkillEffectDefinition.RecipientScope.RESOLVED_TARGETS
                        || effect.recipientScope() == SkillEffectDefinition.RecipientScope.BOTH)
                .findFirst().orElse(null);
    }

    private void startManualSkillCost(BattleActor caster, BattleSkill skill) {
        if (caster == null || skill == null) {
            return;
        }

        caster.startSkillCooldown(skill);
        if (skill.consumesAutoAction()) {
            caster.resetAttackCooldown();
        }
    }

    private double impactFractionFor(BattleActor actor, BattleSkill skill) {
        CharacterModelDefinition.AnimationSlot slot = skill != null
                && skill.getTargetingMode() == Library.BattleTargetingMode.MAGIC
                ? CharacterModelDefinition.AnimationSlot.CAST
                : CharacterModelDefinition.AnimationSlot.ATTACK;
        return actor == null ? CharacterModelDefinition.DEFAULT_IMPACT_FRACTION
                : actor.getCharacterModel().animationBinding(slot).impactFraction();
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

    public Library.BattleResult handleUseItem(InventorySystem.Inventory inventory, int inventoryIndex) {
        BattleActor actor = getFirstLivingAlly();

        if (actor == null) {
            battleMessage = "No allies can act.";
            return Library.BattleResult.DEFEAT;
        }

        if (inventory == null) {
            battleMessage = "No inventory available.";
            return Library.BattleResult.CONTINUE;
        }

        InventorySystem.Item item = inventory.getItem(inventoryIndex);

        if (item == null
                || item.getItemType() != InventorySystem.ItemType.CONSUMABLE
                || item.getHealAmount() <= 0) {
            battleMessage = "That item cannot be used in battle.";
            return Library.BattleResult.CONTINUE;
        }

        int beforeHp = actor.getCurrentHp();
        actor.healDamage(item.getHealAmount());
        int healed = Math.max(0, actor.getCurrentHp() - beforeHp);

        if (healed <= 0) {
            battleMessage = actor.getName() + " is already at full HP.";
            return Library.BattleResult.CONTINUE;
        }

        inventory.removeItem(inventoryIndex);
        playSound(item.getUseSoundPath());
        battleMessage = actor.getName() + " uses " + item.getName() + " and recovers " + healed + " HP.";

        return Library.BattleResult.CONTINUE;
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

        attacker.setPreferredAutoAttackTarget(target);
        battleMessage = attacker.getName() + " will focus " + target.getName() + ".";
        return Library.BattleResult.CONTINUE;
    }

    private void appendBattleMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        battleMessage = battleMessage + " " + message;
    }

    private boolean isDamageSkill(BattleSkill skill) {
        return skill != null && skill.hasEffect("damage");
    }

    private void awardOffensiveSkillExperience(
            BattleActor caster,
            BattleSkill skill,
            CombatResolver.CombatResult combatResult,
            int actualDamage
    ) {
        if (caster == null || skill == null || combatResult == null || !isDamageSkill(skill)) {
            return;
        }

        if (skill.getTargetingMode() == Library.BattleTargetingMode.MAGIC) {
            caster.addCombatSkillExperience(CharacterSkill.MAGIC_ACCURACY, magicAccuracyXpPerCast());
            caster.addCombatSkillExperience(
                    CharacterSkill.MAGIC_POWER,
                    Math.max(magicPowerXpMinimum(), actualDamage * magicPowerXpPerDamage())
            );
            return;
        }

        awardMeleeExperience(caster, actualDamage);
    }

    private void awardMeleeExperience(BattleActor actor, int actualDamage) {
        actor.addCombatSkillExperience(CharacterSkill.ATTACK, attackXpPerAction());
        actor.addCombatSkillExperience(
                CharacterSkill.STRENGTH,
                Math.max(strengthXpMinimum(), actualDamage * strengthXpPerDamage())
        );
    }

    private int defenseXpMinimum() {
        return GameConfiguration.intValue("battle.xp.defense.minimum", 1);
    }

    private int defenseXpPerDamage() {
        return GameConfiguration.intValue("battle.xp.defense.perDamage", 3);
    }

    private int attackXpPerAction() {
        return GameConfiguration.intValue("battle.xp.attack.perAction", 5);
    }

    private int strengthXpMinimum() {
        return GameConfiguration.intValue("battle.xp.strength.minimum", 1);
    }

    private int strengthXpPerDamage() {
        return GameConfiguration.intValue("battle.xp.strength.perDamage", 4);
    }

    private int magicAccuracyXpPerCast() {
        return GameConfiguration.intValue("battle.xp.magicAccuracy.perCast", 5);
    }

    private int magicPowerXpMinimum() {
        return GameConfiguration.intValue("battle.xp.magicPower.minimum", 1);
    }

    private int magicPowerXpPerDamage() {
        return GameConfiguration.intValue("battle.xp.magicPower.perDamage", 4);
    }

    private int magicHealingXpMinimum() {
        return GameConfiguration.intValue("battle.xp.magicHealing.minimum", 4);
    }

    private int magicHealingXpPerHp() {
        return GameConfiguration.intValue("battle.xp.magicHealing.perHp", 4);
    }

    private double debugCriticalHpPercent() {
        return GameConfiguration.doubleValue("battle.debug.criticalHpPercent", 0.10);
    }

    private int debugInvulnerableTurns() {
        return GameConfiguration.intValue("battle.debug.invulnerableTurns", 1);
    }

    private double debugDamageReduction() {
        return GameConfiguration.doubleValue("battle.debug.damageReduction", 1.0);
    }

    private double enemySkillIntelligenceDivisor() {
        return GameConfiguration.doubleValue("battle.enemySkill.intelligenceDivisor", 10.0);
    }

    private int smartEnemyDamageSkillIntelligence() {
        return GameConfiguration.intValue("battle.enemySkill.smartDamageIntelligence", 7);
    }

    private int smartEnemyDebuffIntelligence() {
        return GameConfiguration.intValue("battle.enemySkill.smartDebuffIntelligence", 7);
    }

    public Library.BattleResult handleDebugDropHp(BattleActor target) {
        if (target == null || !target.isAlive()) {
            battleMessage = "No valid debug target.";
            return Library.BattleResult.CONTINUE;
        }

        int criticalHp = Math.max(1, (int) Math.ceil(target.getMaxHp() * debugCriticalHpPercent()));
        target.setCurrentHp(criticalHp);
        target.applyDefend(debugInvulnerableTurns(), debugDamageReduction());
        battleMessage = target.getName() + " drops to critical HP.";
        return Library.BattleResult.CONTINUE;
    }

    public Library.BattleResult updateAutoCombat(int deltaMs, boolean paused) {
        if (pendingImpactResult != Library.BattleResult.CONTINUE
                && !presentationDirector.hasPresentations()) {
            Library.BattleResult result = pendingImpactResult;
            pendingImpactResult = Library.BattleResult.CONTINUE;
            return result;
        }
        if (paused) {
            return Library.BattleResult.CONTINUE;
        }

        double deltaSeconds = Math.max(0, deltaMs) / 1000.0;
        for (BattleActor attacker : allActors()) {
            if (!attacker.isAlive()) {
                continue;
            }

            attacker.tickSkillCooldowns(deltaSeconds);
            attacker.tickAttackCooldown(deltaSeconds);

            if (!attacker.isAttackReady()) {
                continue;
            }

            if (presentationDirector.hasActionFor(attacker)) {
                continue;
            }

            if (attacker.isStunned()) {
                battleMessage = attacker.getName() + " is stunned!";
                attacker.tickStatusDurations();
                attacker.resetAttackCooldown();
                continue;
            }

            if (attacker.isEnemy()) {
                resolveEnemyAutoAction(attacker, new StringBuilder());
            } else {
                resolveAllyAutoAction(attacker, new StringBuilder());
            }
        }
        return Library.BattleResult.CONTINUE;
    }

    private Library.BattleResult resolveAllyAutoAction(BattleActor attacker, StringBuilder turnSummary) {
        BattleActor target = validPreferredTarget(attacker);
        if (target == null) {
            target = getFirstLivingEnemy();
        }

        if (target == null) {
            battleMessage = "Victory!";
            return Library.BattleResult.VICTORY;
        }

        if (!canTarget(attacker, target, Library.BattleTargetingMode.NORMAL_MELEE)) {
            appendSummary(turnSummary, attacker.getName() + " cannot reach " + target.getName() + ".");
            return Library.BattleResult.CONTINUE;
        }

        appendSummary(turnSummary, resolveMeleeAutoAction(attacker, target));
        return Library.BattleResult.CONTINUE;
    }

    private BattleActor validPreferredTarget(BattleActor attacker) {
        BattleActor target = attacker.getPreferredAutoAttackTarget();
        if (target == null || !canTarget(attacker, target, Library.BattleTargetingMode.NORMAL_MELEE)) {
            return null;
        }
        return target;
    }

    private Library.BattleResult resolveEnemyAutoAction(BattleActor attacker, StringBuilder turnSummary) {
        BattleActor target = getFirstLivingAlly();
        if (target == null) {
            battleMessage = "Defeat!";
            return Library.BattleResult.DEFEAT;
        }

        BattleSkill selectedSkill = chooseEnemySkill(attacker);
        if (selectedSkill != null) {
            appendSummary(turnSummary, resolveEnemySkill(attacker, selectedSkill));
        } else {
            appendSummary(turnSummary, resolveMeleeAutoAction(attacker, target));
        }

        return Library.BattleResult.CONTINUE;
    }

    private String resolveMeleeAutoAction(BattleActor attacker, BattleActor target) {
        FirstPersonAnimationRuntime.PresentationTiming timing =
                FirstPersonAnimationRuntime.timing(
                        attacker, BattlePresentationDirector.ActionType.AUTO_ATTACK);
        double impactFraction = timing.durationMs() > 0
                ? timing.impactFraction() : impactFractionFor(attacker, null);
        BattleActionIntent intent = new BattleActionIntent(
                attacker,
                "Auto Attack",
                BattlePresentationDirector.ActionType.AUTO_ATTACK,
                BattleActionIntent.Priority.AUTOMATIC,
                impactFraction,
                timing.durationMs(),
                List.of(target),
                () -> planMeleeOutcome(attacker, target)
        );
        if (!presentationDirector.enqueue(intent)) {
            return "";
        }
        return attacker.getName() + " prepares to attack " + target.getName() + ".";
    }

    private BattleActionIntent.OutcomePlan planMeleeOutcome(BattleActor attacker, BattleActor suggestedTarget) {
        if (attacker == null || !attacker.isAlive()) return null;
        BattleActor target = suggestedTarget;
        if (target == null || !target.isAlive()
                || !canTarget(attacker, target, Library.BattleTargetingMode.NORMAL_MELEE)) {
            target = attacker.isEnemy() ? getFirstLivingAlly() : validPreferredTarget(attacker);
            if (target == null && !attacker.isEnemy()) target = getFirstLivingEnemy();
        }
        if (target == null || !canTarget(attacker, target, Library.BattleTargetingMode.NORMAL_MELEE)) {
            updatePendingBattleResult();
            return null;
        }
        BattleActor resolvedTarget = target;
        CombatResolver.CombatResult result = CombatResolver.resolveMelee(attacker, resolvedTarget);
        int projectedDamage = Math.max(0, result.damage());
        attacker.resetAttackCooldown();
        attacker.tickStatusDurations();
        BattlePresentationDirector.TargetReaction reaction = new BattlePresentationDirector.TargetReaction(
                resolvedTarget, reactionFor(result.hit(), projectedDamage, resolvedTarget), projectedDamage);
        return new BattleActionIntent.OutcomePlan(List.of(reaction), () -> {
            if (!resolvedTarget.isAlive()) {
                updatePendingBattleResult();
                return;
            }
            int damage = resolvedTarget.takeDamage(projectedDamage);
            playSound(attacker.getAttackSoundPath());
            if (result.hit()) playSound(resolvedTarget.getHitSoundPath());
            awardMeleeExperience(attacker, damage);
            resolvedTarget.addCombatSkillExperience(CharacterSkill.DEFENSE,
                    Math.max(defenseXpMinimum(), damage * defenseXpPerDamage()));
            battleMessage = attacker.getName() + " attacks " + resolvedTarget.getName()
                    + " and " + result.text() + "!";
            updatePendingBattleResult();
        });
    }

    private void updatePendingBattleResult() {
        for (BattleActor actor : allActors()) {
            if (!actor.isAlive() && scheduledDeathPresentations.add(actor)) {
                presentationDirector.enqueue(new BattleActionIntent(
                        actor, "Death", BattlePresentationDirector.ActionType.DEATH,
                        BattleActionIntent.Priority.DEATH, 0.60, List.of(),
                        () -> new BattleActionIntent.OutcomePlan(List.of(), () -> { })
                ));
            }
        }
        if (allEnemiesDefeated()) {
            battleMessage = "Victory!";
            pendingImpactResult = Library.BattleResult.VICTORY;
        } else if (getFirstLivingAlly() == null) {
            battleMessage = "Defeat!";
            pendingImpactResult = Library.BattleResult.DEFEAT;
        }
    }

    private BattlePresentationDirector.Reaction reactionFor(boolean hit, int damage, BattleActor target) {
        if (!hit) {
            return BattlePresentationDirector.Reaction.DODGE;
        }
        return damage <= 0 && target != null && target.getDefendingTurns() > 0
                ? BattlePresentationDirector.Reaction.BLOCK
                : BattlePresentationDirector.Reaction.HIT;
    }

    private BattlePresentationDirector.ActionType presentationTypeFor(BattleSkill skill) {
        if (skill == null) {
            return BattlePresentationDirector.ActionType.PHYSICAL_SKILL;
        }
        return switch (skill.getPresentationStyle().trim().toUpperCase()) {
            case "HEAL" -> BattlePresentationDirector.ActionType.HEAL;
            case "DEFEND" -> BattlePresentationDirector.ActionType.DEFEND;
            case "SUMMON" -> BattlePresentationDirector.ActionType.SUMMON;
            case "SPELL" -> BattlePresentationDirector.ActionType.SPELL;
            case "RANGED" -> BattlePresentationDirector.ActionType.RANGED;
            case "PHYSICAL_SKILL" -> BattlePresentationDirector.ActionType.PHYSICAL_SKILL;
            default -> {
                if (skill.isSummonSkill()) yield BattlePresentationDirector.ActionType.SUMMON;
                if (skill.hasEffect("heal") && !skill.hasEffect("damage")) {
                    yield BattlePresentationDirector.ActionType.HEAL;
                }
                yield switch (skill.getTargetingMode()) {
                case MAGIC -> BattlePresentationDirector.ActionType.SPELL;
                case RANGED -> BattlePresentationDirector.ActionType.RANGED;
                default -> BattlePresentationDirector.ActionType.PHYSICAL_SKILL;
                };
            }
        };
    }

    private void appendSummary(StringBuilder builder, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(" ");
        }
        builder.append(message);
    }

    private BattleSkill chooseEnemySkill(BattleActor attacker) {
        if (attacker == null || attacker.getCombatAiIntelligence() <= 0 || attacker.getSkills().isEmpty()) {
            return null;
        }

        double skillChance = attacker.getCombatAiIntelligence() / enemySkillIntelligenceDivisor();

        if (BattleRandom.nextDouble() > skillChance) {
            return null;
        }

        List<BattleSkill> usableSkills = attacker.getSkills().stream()
                .filter(attacker::isSkillReady)
                .filter(skill -> !getSelectableActorsForSkill(attacker, skill).isEmpty())
                .filter(skill -> !skill.isSummonSkill() || canSummon(attacker))
                .filter(skill -> !shouldSmartAvoidDebuffRefresh(attacker, skill))
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

        BattleSkill summonSkill = usableSkills.stream()
                .filter(skill -> hasAiRole(skill, "SUMMON"))
                .findFirst()
                .orElse(null);

        if (summonSkill != null) {
            return summonSkill;
        }

        BattleSkill healSkill = usableSkills.stream()
                .filter(skill -> hasAiRole(skill, "HEAL"))
                .findFirst().orElse(null);
        if (healSkill != null && attacker.getCurrentHp() < attacker.getMaxHp()) {
            return healSkill;
        }

        BattleSkill cleanseSkill = usableSkills.stream()
                .filter(skill -> hasAiRole(skill, "CLEANSE"))
                .findFirst().orElse(null);
        if (cleanseSkill != null && attacker.hasStatusPolarity(StatusDefinition.Polarity.HARMFUL)) {
            return cleanseSkill;
        }

        BattleSkill damageSkill = usableSkills.stream()
                .filter(this::isDamageSkill)
                .findFirst()
                .orElse(null);

        if (damageSkill != null) {
            return damageSkill;
        }

        BattleSkill defendSkill = usableSkills.stream()
                .filter(this::isDefensiveSkill)
                .findFirst()
                .orElse(null);

        if (defendSkill != null && attacker.getCurrentHp() < attacker.getMaxHp() && attacker.getDefendingTurns() <= 0) {
            return defendSkill;
        }

        BattleSkill statusSkill = usableSkills.stream()
                .filter(skill -> hasAiRole(skill, "STATUS"))
                .findFirst().orElse(null);
        if (statusSkill != null) {
            return statusSkill;
        }

        return usableSkills.stream()
                .filter(skill -> !isDefensiveSkill(skill))
                .findFirst()
                .orElse(null);
    }

    private boolean hasAiRole(BattleSkill skill, String role) {
        if (skill == null || role == null) return false;
        return skill.getEffects().stream().anyMatch(effect -> {
            BattleContentTypeRegistry.HandlerDescriptor descriptor =
                    BattleContentTypeRegistry.effectDescriptor(effect.kindId());
            return descriptor != null && role.equals(descriptor.aiRole());
        });
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

        BattleActionIntent intent = new BattleActionIntent(
                attacker, skill.getName(), presentationTypeFor(skill),
                BattleActionIntent.Priority.AUTOMATIC, impactFractionFor(attacker, skill),
                resolvedTargets, () -> planSkillOutcome(attacker, skill, resolvedTargets, true));
        if (!presentationDirector.enqueue(intent)) return "";
        return attacker.getName() + " prepares " + skill.getName() + ".";
    }

    private BattleActor selectEnemySkillTarget(BattleActor attacker, BattleSkill skill, List<BattleActor> targets) {
        List<String> harmfulStatusIds = appliedHarmfulStatusIds(skill);
        if (attacker.getCombatAiIntelligence() >= smartEnemyDebuffIntelligence()
                && !harmfulStatusIds.isEmpty()) {
            List<BattleActor> nonDebuffedTargets = targets.stream()
                    .filter(target -> harmfulStatusIds.stream().anyMatch(statusId -> !target.hasStatus(statusId)))
                    .toList();
            if (!nonDebuffedTargets.isEmpty()) {
                targets = nonDebuffedTargets;
            }
        }

        if (attacker.getCombatAiIntelligence() >= smartEnemyDamageSkillIntelligence()
                && isDamageSkill(skill)) {
            return targets.stream()
                    .min(Comparator.comparingInt(BattleActor::getCurrentHp))
                    .orElse(targets.getFirst());
        }

        return targets.getFirst();
    }

    private boolean shouldSmartAvoidDebuffRefresh(BattleActor attacker, BattleSkill skill) {
        if (attacker == null || skill == null || !hasAiRole(skill, "STATUS")) {
            return false;
        }
        if (attacker.getCombatAiIntelligence() < smartEnemyDebuffIntelligence()) {
            return false;
        }

        List<String> harmfulStatuses = appliedHarmfulStatusIds(skill);
        if (harmfulStatuses.isEmpty()) return false;
        return getSelectableActorsForSkill(attacker, skill).stream()
                .allMatch(target -> harmfulStatuses.stream().allMatch(statusId -> !target.canAcceptStatus(statusId)));
    }

    private boolean isDefensiveSkill(BattleSkill skill) {
        if (skill == null) return false;
        if ("DEFEND".equalsIgnoreCase(skill.getPresentationStyle())) return true;
        return skill.getAppliedStatusIds().stream().anyMatch(statusId -> {
            StatusDefinition definition = BattleContentCatalog.findStatus(statusId);
            return definition != null
                    && definition.polarity() == StatusDefinition.Polarity.BENEFICIAL
                    && "incoming_damage_modifier".equals(definition.behaviorKindId());
        });
    }

    private List<String> appliedHarmfulStatusIds(BattleSkill skill) {
        if (skill == null) return List.of();
        return skill.getAppliedStatusIds().stream()
                .map(BattleContentCatalog::normalizeId)
                .filter(statusId -> {
                    StatusDefinition definition = BattleContentCatalog.findStatus(statusId);
                    return definition != null && definition.polarity() == StatusDefinition.Polarity.HARMFUL;
                })
                .toList();
    }

    private String resolveSummon(BattleActor caster, BattleSkill skill) {
        if (caster == null || skill == null || !skill.isSummonSkill()) {
            return "Nothing answers.";
        }

        if (!canSummon(caster)) {
            return caster.getName() + " calls out, but there is no room.";
        }

        if (BattleRandom.nextDouble() > skill.getSummonChance()) {
            playSound(skill.getUseSoundPath());
            return caster.getName() + "'s " + skill.getName() + " fails.";
        }

        BattleActor summoned = createSummonedActor(caster, skill);
        if (summoned == null) {
            return "Nothing answers.";
        }

        List<BattleActor> sameSide = getActorsOnSameSide(caster);
        if (!assignSummonedActorToOpenPosition(summoned, sameSide)) {
            return caster.getName() + " calls out, but there is no room.";
        }
        sameSide.add(summoned);
        playSound(skill.getUseSoundPath());
        return caster.getName() + " uses " + skill.getName() + ". " + summoned.getName() + " joins the battle!";
    }

    private boolean canSummon(BattleActor caster) {
        return caster != null && getActorsOnSameSide(caster).size() < maxActorsPerSide();
    }

    private BattleActor createSummonedActor(BattleActor caster, BattleSkill skill) {
        return switch (skill.getSummonMode()) {
            case SAME_SPECIES -> createSameSpeciesSummon(caster, skill);
            case SKELETON -> createMonsterActor(MapDesignLibrary.createEnemyById("skeleton"), caster.getEntityType());
            case NONE -> null;
        };
    }

    private BattleActor createSameSpeciesSummon(BattleActor caster, BattleSkill skill) {
        if (caster == null) {
            return null;
        }

        String speciesId = skill == null ? "" : skill.getSummonSpeciesId();
        if (caster.isEnemy() && (speciesId == null || speciesId.isBlank())) {
            return caster.copyForSummon(caster.getName());
        }

        Monster summonedMonster = MapDesignLibrary.createEnemyById(speciesId);
        if (summonedMonster == null) {
            return null;
        }

        return createMonsterActor(summonedMonster, caster.getEntityType());
    }

    private int maxActorsPerSide() {
        return Math.max(1, GameConfiguration.intValue("battle.summon.maxActorsPerSide", 6));
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
