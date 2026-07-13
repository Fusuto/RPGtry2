package org.main.battle;

import org.main.content.SkillLibrary;
import org.main.core.CharacterSkill;
import org.main.core.Library;
import org.main.core.PlayerCharacter;
import org.main.core.PlayerStat;
import org.main.monsters.Monster;

import java.util.List;
import java.util.Map;

public final class DifficultyResolver {
    private static final double OFFENSE_DIVISOR = 8.0;
    private static final double SURVIVAL_DIVISOR = 10.0;
    private static final int MIN_LEVEL = 1;

    private DifficultyResolver() {
    }

    public static DifficultyRating ratePlayer(PlayerCharacter player) {
        if (player == null) {
            return emptyRating();
        }

        BattleActor actor = new BattleActor(
                player.getName(),
                player.getMaxHp(),
                player.getMaxHp(),
                null,
                Library.EntityType.ALLY
        );
        actor.copyCombatProfileFrom(player);
        for (BattleSkill skill : player.getBattleSkills()) {
            actor.addSkill(skill);
        }
        return rateActor(actor);
    }

    public static DifficultyRating rateMonster(Monster monster) {
        if (monster == null) {
            return emptyRating();
        }

        return rateMonsterProfile(monster.getName(), monster.getStatsView(), monster.getSkills());
    }

    public static DifficultyRating rateMonsterProfile(
            String name,
            Map<PlayerStat, Integer> stats,
            List<SkillLibrary> skills
    ) {
        BattleActor actor = createMonsterProfile(name, stats, skills);
        return rateActor(actor);
    }

    public static BattleActor createMonsterProfile(
            String name,
            Map<PlayerStat, Integer> stats,
            List<SkillLibrary> skills
    ) {
        BattleActor actor = new BattleActor(
                name == null || name.isBlank() ? "Enemy" : name,
                Math.max(1, stats == null ? 1 : stats.getOrDefault(PlayerStat.VITALITY, 1)),
                Math.max(1, stats == null ? 1 : stats.getOrDefault(PlayerStat.VITALITY, 1)),
                null,
                Library.EntityType.ENEMY,
                stats == null ? 0 : stats.getOrDefault(PlayerStat.STRENGTH, 0),
                stats == null ? 0 : stats.getOrDefault(PlayerStat.DEFENSE, 0)
        );
        actor.configureMonsterCombatStats(stats);
        if (skills != null) {
            skills.stream()
                    .map(SkillLibrary::createSkill)
                    .forEach(actor::addSkill);
        }
        return actor;
    }

    public static DifficultyRating rateActor(BattleActor actor) {
        if (actor == null) {
            return emptyRating();
        }

        double meleePackage = actor.getAttackStat()
                + actor.getCombatSkillLevel(CharacterSkill.ATTACK)
                + actor.getStrengthStat()
                + actor.getCombatSkillLevel(CharacterSkill.STRENGTH)
                + actor.getWeaponBonus();
        double magicPackage = actor.getIntelligence()
                + actor.getWillpowerStat()
                + actor.getCombatSkillLevel(CharacterSkill.MAGIC_ACCURACY)
                + actor.getCombatSkillLevel(CharacterSkill.MAGIC_POWER);
        double offensivePower = Math.max(meleePackage, magicPackage) / OFFENSE_DIVISOR;
        double defensivePower = (actor.getDefenseStat()
                + actor.getCombatSkillLevel(CharacterSkill.DEFENSE)
                + actor.getArmorBonus()
                + actor.getMaxHp()) / SURVIVAL_DIVISOR;
        double utilityPower = 0.0;
        double power = Math.max(1.0, offensivePower + defensivePower);
        int level = Math.max(MIN_LEVEL, (int) Math.floor(power));
        return new DifficultyRating(level, power, offensivePower, defensivePower, utilityPower);
    }

    public static DifficultyComparison compare(DifficultyRating playerRating, DifficultyRating monsterRating) {
        DifficultyRating safePlayer = playerRating == null ? emptyRating() : playerRating;
        DifficultyRating safeMonster = monsterRating == null ? emptyRating() : monsterRating;
        double ratio = safeMonster.power() / Math.max(1.0, safePlayer.power());
        return new DifficultyComparison(safePlayer, safeMonster, ratio, DifficultyBand.fromRatio(ratio));
    }

    private static DifficultyRating emptyRating() {
        return new DifficultyRating(MIN_LEVEL, 1.0, 0.0, 0.0, 0.0);
    }

    public record DifficultyRating(
            int level,
            double power,
            double offensivePower,
            double defensivePower,
            double utilityPower
    ) {
    }

    public record DifficultyComparison(
            DifficultyRating playerRating,
            DifficultyRating monsterRating,
            double ratio,
            DifficultyBand band
    ) {
        public String compactLabel() {
            return "Lv " + monsterRating.level() + " " + band.getDisplayName();
        }
    }

    public enum DifficultyBand {
        TRIVIAL("Trivial", 0.55),
        EASY("Easy", 0.80),
        FAIR("Fair", 1.15),
        DANGEROUS("Dangerous", 1.45),
        DEADLY("Deadly", Double.MAX_VALUE);

        private final String displayName;
        private final double maximumRatio;

        DifficultyBand(String displayName, double maximumRatio) {
            this.displayName = displayName;
            this.maximumRatio = maximumRatio;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static DifficultyBand fromRatio(double ratio) {
            for (DifficultyBand band : values()) {
                if (ratio <= band.maximumRatio) {
                    return band;
                }
            }
            return DEADLY;
        }
    }
}
