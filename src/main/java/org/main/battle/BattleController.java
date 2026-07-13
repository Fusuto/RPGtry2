package org.main.battle;

import org.main.core.GameState;
import org.main.core.InventorySystem;
import org.main.core.InteractionSystem;
import org.main.core.Library;
import org.main.content.SkillLibrary;
import org.main.content.QuestLibrary;
import org.main.content.EnvironmentLibrary;
import org.main.content.MobDropsLibrary;
import org.main.engine.MapEntity;
import org.main.engine.SoundSystem;
import org.main.monsters.MonsterType;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.List;

public class BattleController {
    private static final String LOW_HP_WARNING_SOUND_PATH = "assets/sounds/generated/kurt_sample_2.wav";
    private static final double LOW_HP_WARNING_THRESHOLD = 0.10;

    private final GameState gameState;
    private final BattleRenderer battleRenderer;
    private final SoundSystem soundSystem;
    private final EnvironmentLibrary environment;

    private BattleSkill pendingSkill = null;
    private Library.BattleCommand pendingBattleCommand = null;

    public BattleController(GameState gameState, BattleRenderer battleRenderer) {
        this(gameState, battleRenderer, null, null);
    }

    public BattleController(
            GameState gameState,
            BattleRenderer battleRenderer,
            SoundSystem soundSystem,
            EnvironmentLibrary environment
    ) {
        this.gameState = gameState;
        this.battleRenderer = battleRenderer;
        this.soundSystem = soundSystem;
        this.environment = environment;
    }

    public void handleMouseMoved(Point point) {
        battleRenderer.setMousePoint(point);
    }

    public void update() {
        if (soundSystem == null) {
            return;
        }

        if (!gameState.isBattleMode() || gameState.getCurrentEncounter() == null) {
            soundSystem.stopLoopingSound();
            return;
        }

        BattleActor playerActor = gameState.getCurrentEncounter().getFirstLivingAlly();

        if (playerActor == null || playerActor.getMaxHp() <= 0) {
            soundSystem.stopLoopingSound();
            return;
        }

        if ((double) playerActor.getCurrentHp() / (double) playerActor.getMaxHp() <= LOW_HP_WARNING_THRESHOLD) {
            soundSystem.playLoopingSound(LOW_HP_WARNING_SOUND_PATH);
        } else {
            soundSystem.stopLoopingSound();
        }
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

        if (battleRenderer.isItemWindowOpen()) {
            handleItemWindowClick(point);
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
            case KeyEvent.VK_3, KeyEvent.VK_I -> handleBattleCommand(Library.BattleCommand.ITEMS);
            case KeyEvent.VK_4, KeyEvent.VK_R -> handleBattleCommand(Library.BattleCommand.RUN);
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

    private void handleItemWindowClick(Point clickPoint) {
        if (battleRenderer.isItemWindowCloseButtonAt(clickPoint)) {
            battleRenderer.closeItemWindow();
            return;
        }

        Integer inventoryIndex = battleRenderer.getBattleItemIndexAt(clickPoint);

        if (inventoryIndex == null) {
            return;
        }

        BattleEncounter currentEncounter = gameState.getCurrentEncounter();

        if (currentEncounter == null) {
            return;
        }

        Library.BattleResult result = currentEncounter.handleUseItem(
                gameState.getInventory(),
                inventoryIndex
        );

        battleRenderer.closeItemWindow();
        handleBattleResult(result);
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

        if (SkillLibrary.isDebugDropHpSkill(pendingSkill)) {
            Library.BattleResult result = currentEncounter.handleDebugDropHp(selectedActor);

            pendingSkill = null;
            battleRenderer.clearSelectableTargets();
            battleRenderer.clearPreviewSkill();

            handleBattleResult(result);
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

        if (command == Library.BattleCommand.ITEMS) {
            openItemWindow(currentEncounter);
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
        battleRenderer.closeItemWindow();
        battleRenderer.setSelectableTargets(validTargets);
        battleRenderer.clearPreviewSkill();

        currentEncounter.setBattleMessage("Choose a target.");
    }

    private void openSkillWindow(BattleEncounter currentEncounter) {
        pendingSkill = null;
        pendingBattleCommand = null;

        battleRenderer.clearSelectableTargets();
        battleRenderer.closeItemWindow();
        battleRenderer.openSkillWindow();

        currentEncounter.setBattleMessage("Choose a skill.");
    }

    private void openItemWindow(BattleEncounter currentEncounter) {
        pendingSkill = null;
        pendingBattleCommand = null;

        battleRenderer.clearSelectableTargets();
        battleRenderer.closeSkillWindow();
        battleRenderer.openItemWindow(gameState.getInventory());

        currentEncounter.setBattleMessage("Choose an item.");
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
        int experienceReward = 0;
        MonsterType defeatedMonsterType = null;

        if (removeEnemy && gameState.getCurrentEncounter() != null) {
            experienceReward = gameState.getCurrentEncounter().getDefeatedEnemyExperienceReward();
        }

        int hpBeforeBattle = gameState.getPlayerCharacter().getCurrHp();
        syncPlayerCharacterHp();
        int hpLost = Math.max(0, hpBeforeBattle - gameState.getPlayerCharacter().getCurrHp());

        if (removeEnemy && gameState.getCurrentEnemyEntity() != null) {
            MapEntity defeatedEnemy = gameState.getCurrentEnemyEntity();
            defeatedMonsterType = defeatedEnemy.getMonster() == null ? null : defeatedEnemy.getMonster().getType();

            if (defeatedEnemy.getMonster() != null
                    && defeatedEnemy.getMonster().getType() != null
                    && "SLIME".equals(defeatedEnemy.getMonster().getType().name())
                    && gameState.getQuestStage(QuestLibrary.SKELETON_HAT) == 1) {
                gameState.setQuestStage(QuestLibrary.SKELETON_HAT, 2);
            }

            gameState.removeEntity(defeatedEnemy);
            spawnLootDrops(defeatedEnemy);
        }

        // Class leveling is intentionally paused while the limb progression system replaces classes.

        pendingSkill = null;
        pendingBattleCommand = null;

        battleRenderer.closeSkillWindow();
        battleRenderer.closeItemWindow();
        battleRenderer.clearSelectableTargets();
        battleRenderer.clearPreviewSkill();

        if (!removeEnemy && gameState.getPlayerCharacter().getCurrHp() <= 0) {
            gameState.enterGameOver();
        } else {
            gameState.clearBattleState();

            if (removeEnemy && defeatedMonsterType != null) {
                gameState.openInteraction(InteractionSystem.postBattleMenu(gameState, defeatedMonsterType, experienceReward, hpLost));
            }
        }

        if (soundSystem != null) {
            soundSystem.stopLoopingSound();
            soundSystem.stopMusic();

            if (environment != null && !gameState.isGameOverMode()) {
                soundSystem.playAmbience(environment.getAmbienceSoundPath());
            }
        }
    }

    private void syncPlayerCharacterHp() {
        BattleEncounter currentEncounter = gameState.getCurrentEncounter();

        if (currentEncounter == null || currentEncounter.getAllies().isEmpty()) {
            return;
        }

        BattleActor playerActor = currentEncounter.getAllies().get(0);
        gameState.getPlayerCharacter().setCurrHp(playerActor.getCurrentHp());
    }

    private void spawnLootDrops(MapEntity defeatedEnemy) {
        if (defeatedEnemy == null || defeatedEnemy.getMonster() == null || defeatedEnemy.getMonster().getType() == null) {
            return;
        }

        for (InventorySystem.Item item : MobDropsLibrary.rollDrops(defeatedEnemy.getMonster().getType())) {
            gameState.addEntity(new MapEntity(item, defeatedEnemy.getX(), defeatedEnemy.getY()));
        }
    }
}
