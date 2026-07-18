package org.main.battle;

import org.main.core.GameState;
import org.main.core.GameConfiguration;
import org.main.core.InventorySystem;
import org.main.core.InteractionSystem;
import org.main.core.Library;
import org.main.content.SkillLibrary;
import org.main.core.GameEnvironment;
import org.main.engine.MapEntity;
import org.main.engine.SoundSystem;
import org.main.monsters.Monster;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.List;

public class BattleController {
    private final GameState gameState;
    private final BattleRenderer battleRenderer;
    private final SoundSystem soundSystem;
    private final GameEnvironment environment;

    private BattleSkill pendingSkill = null;
    private Library.BattleCommand pendingBattleCommand = null;

    public BattleController(
            GameState gameState,
            BattleRenderer battleRenderer,
            SoundSystem soundSystem,
            GameEnvironment environment
    ) {
        this.gameState = gameState;
        this.battleRenderer = battleRenderer;
        this.soundSystem = soundSystem;
        this.environment = environment;
    }

    public void handleMouseMoved(Point point) {
        battleRenderer.setMousePoint(point);
    }

    public void update(int deltaMs) {
        if (!gameState.isBattleMode() || gameState.getCurrentEncounter() == null) {
            if (soundSystem != null) {
                soundSystem.stopLoopingSound();
            }
            battleRenderer.setAutoCombatPaused(false);
            return;
        }

        BattleActor playerActor = gameState.getCurrentEncounter().getFirstLivingAlly();

        if (playerActor == null || playerActor.getMaxHp() <= 0) {
            if (soundSystem != null) {
                soundSystem.stopLoopingSound();
            }
            return;
        }

        if (soundSystem != null) {
            if ((double) playerActor.getCurrentHp() / (double) playerActor.getMaxHp()
                    <= GameConfiguration.doubleValue("battle.lowHpWarning.threshold", 0.10)) {
                soundSystem.playLoopingSound(GameConfiguration.stringValue(
                        "battle.lowHpWarning.soundPath",
                        "assets/sounds/generated/kurt_sample_2.wav"
                ));
            } else {
                soundSystem.stopLoopingSound();
            }
        }

        boolean paused = isAutoCombatPaused();
        battleRenderer.setAutoCombatPaused(paused);
        Library.BattleResult result = gameState.getCurrentEncounter().updateAutoCombat(deltaMs, paused);
        handleBattleResult(result);
    }

    private boolean isAutoCombatPaused() {
        return battleRenderer.isSkillWindowOpen()
                || battleRenderer.isItemWindowOpen()
                || pendingSkill != null
                || pendingBattleCommand != null;
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

    public void handleBattleCommand(Library.BattleCommand command) {
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

    public boolean isSkillWindowOpen() {
        return battleRenderer.isSkillWindowOpen();
    }

    public boolean isItemWindowOpen() {
        return battleRenderer.isItemWindowOpen();
    }

    public BattleSkill getPendingSkill() {
        return pendingSkill;
    }

    public Library.BattleCommand getPendingBattleCommand() {
        return pendingBattleCommand;
    }

    public List<BattleActor> getSelectableTargetsView() {
        return battleRenderer.getSelectableTargetsView();
    }

    public List<BattleSkill> getSkillChoicesView() {
        return battleRenderer.getSkillChoices(gameState.getCurrentEncounter());
    }

    public List<BattleRenderer.BattleItemEntry> getBattleItemsView() {
        return battleRenderer.getBattleItemsView();
    }

    public void selectSkill(BattleSkill skill) {
        if (skill != null) {
            beginSkillTargeting(skill);
        }
    }

    public void selectBattleItem(int inventoryIndex) {
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

    public void selectBattleActor(BattleActor selectedActor) {
        BattleEncounter currentEncounter = gameState.getCurrentEncounter();

        if (currentEncounter == null || selectedActor == null) {
            return;
        }

        if (pendingSkill != null) {
            selectSkillTarget(currentEncounter, selectedActor);
            return;
        }

        if (pendingBattleCommand == Library.BattleCommand.ATTACK) {
            Library.BattleResult result = currentEncounter.handleAttack(selectedActor);

            pendingBattleCommand = null;
            battleRenderer.clearSelectableTargets();

            handleBattleResult(result);
        }
    }

    public boolean hasCurrentBattleChoices() {
        return battleRenderer.isSkillWindowOpen()
                || battleRenderer.isItemWindowOpen()
                || pendingSkill != null
                || pendingBattleCommand != null;
    }

    public void selectCurrentBattleChoice(int choiceIndex) {
        if (choiceIndex < 0) {
            return;
        }

        if (battleRenderer.isSkillWindowOpen()) {
            List<BattleSkill> skills = getSkillChoicesView();
            if (choiceIndex < skills.size()) {
                selectSkill(skills.get(choiceIndex));
            }
            return;
        }

        if (battleRenderer.isItemWindowOpen()) {
            List<BattleRenderer.BattleItemEntry> items = getBattleItemsView();
            if (choiceIndex < items.size()) {
                selectBattleItem(items.get(choiceIndex).inventoryIndex());
            }
            return;
        }

        List<BattleActor> targets = getSelectableTargetsView();
        if (choiceIndex < targets.size()) {
            selectBattleActor(targets.get(choiceIndex));
        }
    }

    public void cancelBattleSelection() {
        pendingSkill = null;
        pendingBattleCommand = null;
        battleRenderer.closeSkillWindow();
        battleRenderer.closeItemWindow();
        battleRenderer.clearSelectableTargets();
        battleRenderer.clearPreviewSkill();
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

        selectSkill(clickedSkill);
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

        selectBattleItem(inventoryIndex);
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

        if (!caster.isSkillReady(skill)) {
            currentEncounter.setBattleMessage(skill.getName() + " is not ready.");
            battleRenderer.closeSkillWindow();
            battleRenderer.clearPreviewSkill();
            return;
        }

        if (skill.isSummonSkill()) {
            Library.BattleResult result = currentEncounter.handleSkill(
                    caster,
                    skill,
                    List.of()
            );

            pendingBattleCommand = null;
            pendingSkill = null;
            battleRenderer.closeSkillWindow();
            battleRenderer.clearSelectableTargets();
            battleRenderer.clearPreviewSkill();

            handleBattleResult(result);
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

        selectBattleActor(selectedActor);
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

        selectBattleActor(target);
    }

    private void selectSkillTarget(BattleEncounter currentEncounter, BattleActor selectedActor) {
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
        Monster defeatedMonster = null;

        if (removeEnemy && gameState.getCurrentEncounter() != null) {
            experienceReward = gameState.getCurrentEncounter().getDefeatedEnemyExperienceReward();
        }

        int hpBeforeBattle = gameState.getPlayerCharacter().getCurrHp();
        syncPlayerCharacterHp();
        int hpLost = Math.max(0, hpBeforeBattle - gameState.getPlayerCharacter().getCurrHp());

        if (removeEnemy && gameState.getCurrentEnemyEntity() != null) {
            MapEntity defeatedEnemy = gameState.getCurrentEnemyEntity();
            defeatedMonster = defeatedEnemy.getMonster();

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

            if (removeEnemy && defeatedMonster != null) {
                gameState.openInteraction(InteractionSystem.postBattleMenu(gameState, defeatedMonster, experienceReward, hpLost));
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

        BattleActor playerActor = currentEncounter.getAllies().getFirst();
        gameState.getPlayerCharacter().setCurrHp(playerActor.getCurrentHp());
    }

    private void spawnLootDrops(MapEntity defeatedEnemy) {
        if (defeatedEnemy == null || defeatedEnemy.getMonster() == null) {
            return;
        }

        Monster monster = defeatedEnemy.getMonster();
        for (Monster.DropEntry drop : monster.getCustomDrops()) {
            if (!drop.rolls()) {
                continue;
            }
            InventorySystem.Item item = gameState.createItemByNameOrId(drop.itemId());
            if (item != null) {
                gameState.addEntity(new MapEntity(item, defeatedEnemy.getX(), defeatedEnemy.getY()));
            }
        }
    }
}
