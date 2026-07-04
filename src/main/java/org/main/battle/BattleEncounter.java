package org.main.battle;

import org.main.monsters.Monster;

import java.util.List;

public class BattleEncounter {
    private final List<BattleActor> allies;
    private final List<BattleActor> enemies;

    private String battleMessage = "Battle begins!";

    public BattleEncounter(List<BattleActor> allies, List<BattleActor> enemies) {
        this.allies = allies;
        this.enemies = enemies;
    }

    public static BattleEncounter fromMonster(Monster monster) {
        BattleActor playerActor = new BattleActor(
                "Player",
                30,
                30,
                null,
                false
        );

        BattleActor enemyActor = new BattleActor(
                monster.getName(),
                monster.getMaxHp(),
                monster.getCurrentHp(),
                monster.getType().getImg(),
                true
        );

        return new BattleEncounter(
                List.of(playerActor),
                List.of(enemyActor)
        );
    }

    public BattleResult handleCommand(BattleCommand command) {
        return switch (command) {
            case ATTACK -> handleAttack();
            case SKILL -> handleSkill();
            case RUN -> handleRun();
        };
    }

    private BattleResult handleAttack() {
        BattleActor attacker = getFirstLivingAlly();
        BattleActor target = getFirstLivingEnemy();

        if (attacker == null) {
            battleMessage = "No allies can act.";
            return BattleResult.DEFEAT;
        }

        if (target == null) {
            battleMessage = "There are no enemies left.";
            return BattleResult.VICTORY;
        }

        int damage = 5; // placeholder until player stats exist

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

    private BattleResult handleSkill() {
        battleMessage = "No skills implemented yet.";
        return BattleResult.CONTINUE;
    }

    private BattleResult handleRun() {
        battleMessage = "You ran away.";
        return BattleResult.RAN;
    }

    private BattleActor getFirstLivingAlly() {
        for (BattleActor ally : allies) {
            if (ally.isAlive()) {
                return ally;
            }
        }

        return null;
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