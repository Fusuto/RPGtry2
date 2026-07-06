package org.main.battle;

import org.main.core.GameState;
import org.main.core.Library;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.List;

public class BattleController {
    private final GameState gameState;
    private final BattleRenderer battleRenderer;

    private BattleSkill pendingSkill = null;
    private Library.BattleCommand pendingBattleCommand = null;

    public BattleController(GameState gameState, BattleRenderer battleRenderer) {
        this.gameState = gameState;
        this.battleRenderer = battleRenderer;
    }

    public void handleMouseMoved(Point point) {
        battleRenderer.setMousePoint(point);
    }

    public void handleMouseClick(Point point) {
        battleRenderer.setMousePoint(point);

        if (!gameState.isBattleMode()) {
            return;
        }

        if (battleRenderer.isSkillWindowOpen()) {
            handleSkillWindowClick(point);
            return;
        }

        if (pendingSkill != null) {
            handleSkillTargetClick(point);
            return;
        }

        if (pendingBattleCommand != null) {
            handleTargetClick(point);
            return;
        }

        Library.BattleCommand command = battleRenderer.getCommandAt(point);

        if (command != null) {
            handleBattleCommand(command);
        }
    }

    public void handleInput(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_1, KeyEvent.VK_A -> handleBattleCommand(Library.BattleCommand.ATTACK);
            case KeyEvent.VK_2, KeyEvent.VK_S -> handleBattleCommand(Library.BattleCommand.SKILL);
            case KeyEvent.VK_3, KeyEvent.VK_R -> handleBattleCommand(Library.BattleCommand.RUN);
            case KeyEvent.VK_ESCAPE -> endBattle(false);
        }
    }

    private void handleSkillWindowClick(Point clickPoint) {
        if (battleRenderer.isSkillWindowCloseButtonAt(clickPoint)) {
            battleRenderer.closeSkillWindow();
            return;
        }

        BattleSkill clickedSkill = battleRenderer.getSkillAt(clickPoint);

        if (clickedSkill == null) {
            return;
        }

        beginSkillTargeting(clickedSkill);
    }

    private void beginSkillTargeting(BattleSkill skill) {
        BattleEncounter currentEncounter = gameState.getCurrentEncounter();

        if (currentEncounter == null) {
            return;
        }

        BattleActor caster = currentEncounter.getFirstLivingAlly();

        if (caster == null) {
            currentEncounter.setBattleMessage("No one can use that skill.");
            battleRenderer.closeSkillWindow();
            return;
        }

        List<BattleActor> selectableTargets = currentEncounter.getSelectableActorsForSkill(
                caster,
                skill
        );

        if (selectableTargets.isEmpty()) {
            currentEncounter.setBattleMessage("No valid targets.");
            battleRenderer.closeSkillWindow();
            return;
        }

        pendingBattleCommand = null;
        pendingSkill = skill;

        battleRenderer.closeSkillWindow();
        battleRenderer.setSelectableTargets(selectableTargets);
        battleRenderer.setPreviewSkill(skill);

        currentEncounter.setBattleMessage("Choose a target for " + skill.getName() + ".");
    }

    private void handleSkillTargetClick(Point clickPoint) {
        BattleEncounter currentEncounter = gameState.getCurrentEncounter();

        if (currentEncounter == null) {
            return;
        }

        BattleActor selectedActor = battleRenderer.getActorAt(clickPoint);

        if (selectedActor == null) {
            return;
        }

        BattleActor caster = currentEncounter.getFirstLivingAlly();

        if (!currentEncounter.canSelectActorForSkill(caster, selectedActor, pendingSkill)) {
            currentEncounter.setBattleMessage("Invalid target.");
            return;
        }

        List<BattleActor> resolvedTargets = currentEncounter.resolveSkillTargets(
                caster,
                selectedActor,
                pendingSkill
        );

        Library.BattleResult result = currentEncounter.handleSkill(
                caster,
                pendingSkill,
                resolvedTargets
        );

        pendingSkill = null;
        battleRenderer.clearSelectableTargets();
        battleRenderer.clearPreviewSkill();

        handleBattleResult(result);
    }

    private void handleTargetClick(Point clickPoint) {
        BattleEncounter currentEncounter = gameState.getCurrentEncounter();

        if (currentEncounter == null) {
            return;
        }

        BattleActor target = battleRenderer.getActorAt(clickPoint);

        if (target == null) {
            return;
        }

        if (pendingBattleCommand == Library.BattleCommand.ATTACK) {
            Library.BattleResult result = currentEncounter.handleAttack(target);

            pendingBattleCommand = null;
            battleRenderer.clearSelectableTargets();

            handleBattleResult(result);
        }
    }

    private void handleBattleCommand(Library.BattleCommand command) {
        BattleEncounter currentEncounter = gameState.getCurrentEncounter();

        if (currentEncounter == null) {
            return;
        }

        if (command == Library.BattleCommand.ATTACK) {
            beginAttackTargeting(currentEncounter);
            return;
        }

        if (command == Library.BattleCommand.SKILL) {
            openSkillWindow(currentEncounter);
            return;
        }

        Library.BattleResult result = currentEncounter.handleRunCommand(command);
        handleBattleResult(result);
    }

    private void beginAttackTargeting(BattleEncounter currentEncounter) {
        BattleActor attacker = currentEncounter.getFirstLivingAlly();

        List<BattleActor> validTargets = currentEncounter.getValidTargets(
                attacker,
                Library.BattleTargetingMode.NORMAL_MELEE
        );

        pendingSkill = null;
        pendingBattleCommand = Library.BattleCommand.ATTACK;

        battleRenderer.closeSkillWindow();
        battleRenderer.setSelectableTargets(validTargets);
        battleRenderer.clearPreviewSkill();

        currentEncounter.setBattleMessage("Choose a target.");
    }

    private void openSkillWindow(BattleEncounter currentEncounter) {
        pendingSkill = null;
        pendingBattleCommand = null;

        battleRenderer.clearSelectableTargets();
        battleRenderer.openSkillWindow();

        currentEncounter.setBattleMessage("Choose a skill.");
    }

    private void handleBattleResult(Library.BattleResult result) {
        switch (result) {
            case CONTINUE -> {
            }
            case VICTORY -> endBattle(true);
            case RAN, DEFEAT -> endBattle(false);
        }
    }

    private void endBattle(boolean removeEnemy) {
        if (removeEnemy && gameState.getCurrentEnemyEntity() != null) {
            gameState.removeEntity(gameState.getCurrentEnemyEntity());
        }

        pendingSkill = null;
        pendingBattleCommand = null;

        battleRenderer.closeSkillWindow();
        battleRenderer.clearSelectableTargets();
        battleRenderer.clearPreviewSkill();

        gameState.clearBattleState();
    }
}