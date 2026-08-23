package org.main.battle;

import org.main.content.BattleContentCatalog;
import org.main.content.BattleContentTypeRegistry;
import org.main.core.CharacterSkill;
import org.main.core.GameConfiguration;
import org.main.core.Library;
import org.main.core.PlayerCharacter;
import org.main.core.PlayerStat;
import org.main.monsters.Monster;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.LinkedHashMap;

public final class DifficultyResolver {
    private static final Map<PlayerCharacter, CachedRating> PLAYER_CACHE = new WeakHashMap<>();
    private static final Map<Monster, CachedRating> MONSTER_CACHE = new WeakHashMap<>();
    private static final Map<ProfileKey, DifficultyRating> PROFILE_CACHE =
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ProfileKey, DifficultyRating> eldest) {
                    return size() > 512;
                }
            };

    private DifficultyResolver() {
    }

    public static DifficultyRating ratePlayer(PlayerCharacter player) {
        if (player == null) {
            return emptyRating();
        }

        long contextRevision = contextRevision();
        int signature = playerSignature(player);
        synchronized (PLAYER_CACHE) {
            CachedRating cached = PLAYER_CACHE.get(player);
            if (cached != null && cached.contextRevision() == contextRevision
                    && cached.signature() == signature) {
                return cached.rating();
            }
        }

        BattleActor actor = new BattleActor(
                player.getName(),
                player.getMaxHp(),
                player.getMaxHp(),
                null,
                Library.EntityType.ALLY
        );
        actor.setAttackStat(player.getStat(PlayerStat.ATTACK));
        actor.setStrengthStat(player.getStat(PlayerStat.STRENGTH));
        actor.setDefenseStat(player.getStat(PlayerStat.DEFENSE));
        actor.setAgilityStat(player.getStat(PlayerStat.AGILITY));
        actor.setIntelligence(player.getStat(PlayerStat.INTELLIGENCE));
        actor.setWillpowerStat(player.getStat(PlayerStat.WILLPOWER));
        actor.setArmorBonus(0);
        actor.setWeaponAccuracyBonus(0);
        actor.setWeaponPowerBonus(0);
        actor.setWeaponSpeedMultiplier(1.0);
        actor.setCombatSkillLevel(CharacterSkill.ATTACK, player.getSkillLevel(CharacterSkill.ATTACK));
        actor.setCombatSkillLevel(CharacterSkill.STRENGTH, player.getSkillLevel(CharacterSkill.STRENGTH));
        actor.setCombatSkillLevel(CharacterSkill.DEFENSE, player.getSkillLevel(CharacterSkill.DEFENSE));
        actor.setCombatSkillLevel(CharacterSkill.MAGIC_ACCURACY, player.getSkillLevel(CharacterSkill.MAGIC_ACCURACY));
        actor.setCombatSkillLevel(CharacterSkill.MAGIC_POWER, player.getSkillLevel(CharacterSkill.MAGIC_POWER));
        for (BattleSkill skill : player.getBattleSkills()) {
            actor.addSkill(skill);
        }
        DifficultyRating rating = rateActor(actor);
        synchronized (PLAYER_CACHE) {
            PLAYER_CACHE.put(player, new CachedRating(contextRevision, signature, rating));
        }
        return rating;
    }

    public static DifficultyRating rateMonster(Monster monster) {
        if (monster == null) {
            return emptyRating();
        }
        long contextRevision = contextRevision();
        synchronized (MONSTER_CACHE) {
            CachedRating cached = MONSTER_CACHE.get(monster);
            if (cached != null && cached.contextRevision() == contextRevision) {
                return cached.rating();
            }
        }
        DifficultyRating rating = rateMonsterProfile(
                monster.getName(), monster.getStatsView(), monster.getSkillIds());
        synchronized (MONSTER_CACHE) {
            MONSTER_CACHE.put(monster, new CachedRating(contextRevision, 0, rating));
        }
        return rating;
    }

    public static DifficultyRating rateMonsterProfile(
            String name,
            Map<PlayerStat, Integer> stats,
            List<String> skillIds
    ) {
        ProfileKey key = new ProfileKey(name, stats, skillIds, contextRevision());
        synchronized (PROFILE_CACHE) {
            DifficultyRating cached = PROFILE_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        BattleActor actor = createMonsterProfile(name, stats, skillIds);
        DifficultyRating rating = rateActor(actor);
        synchronized (PROFILE_CACHE) {
            PROFILE_CACHE.put(key, rating);
        }
        return rating;
    }

    public static BattleActor createMonsterProfile(
            String name,
            Map<PlayerStat, Integer> stats,
            List<String> skillIds
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
        if (skillIds != null) {
            skillIds.stream()
                    .map(BattleContentCatalog::createSkill)
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
                + actor.getWeaponAccuracyBonus()
                + actor.getWeaponPowerBonus();
        double baselineAttackInterval = BattleTiming.calculateAttackIntervalSeconds(
                GameConfiguration.intValue("battle.attackInterval.minimumAgility", 1)
        );
        double actorAttackInterval = BattleTiming.calculateAttackIntervalSeconds(
                actor.getAgilityStat(),
                actor.getWeaponSpeedMultiplier()
        );
        double speedMultiplier = actorAttackInterval <= 0.0
                ? 1.0
                : baselineAttackInterval / actorAttackInterval;
        speedMultiplier = Math.max(1.0, Math.min(speedMultiplier, speedMultiplierCap()));
        double autoAttackPower = meleePackage * speedMultiplier / offenseDivisor();

        double magicPackage = actor.getIntelligence()
                + actor.getWillpowerStat()
                + actor.getCombatSkillLevel(CharacterSkill.MAGIC_ACCURACY)
                + actor.getCombatSkillLevel(CharacterSkill.MAGIC_POWER);
        double magicPower = magicPackage / offenseDivisor();
        double offensivePower = Math.max(autoAttackPower, magicPower);
        double defensivePower = (actor.getDefenseStat()
                + actor.getCombatSkillLevel(CharacterSkill.DEFENSE)
                + actor.getArmorBonus()
                + actor.getMaxHp()) / survivalDivisor();
        double utilityPower = actor.getSkills().stream()
                .mapToDouble(DifficultyResolver::skillUtility)
                .sum();
        double power = Math.max(1.0, offensivePower + defensivePower + utilityPower);
        int level = Math.max(minLevel(), (int) Math.floor(power));
        return new DifficultyRating(level, power, offensivePower, defensivePower, utilityPower);
    }

    private static double skillUtility(BattleSkill skill) {
        if (skill == null) return 0.0;
        double cooldownWeight = 1.0 / Math.max(1.0, skill.getBaseCooldownSeconds() / 5.0);
        return skill.getEffects().stream().mapToDouble(effect -> {
            BattleContentTypeRegistry.HandlerDescriptor descriptor =
                    BattleContentTypeRegistry.effectDescriptor(effect.kindId());
            if (descriptor == null) return 0.0;
            double base = switch (descriptor.aiRole()) {
                case "OFFENSE" -> effect.intParameter("potency", 1) * 0.10;
                case "HEAL" -> effect.intParameter("potency", 3) * 0.10;
                case "STATUS" -> 0.75 * effect.chance();
                case "CLEANSE" -> 0.60;
                case "SUMMON" -> effect.doubleParameter("successPercent", 50) / 100.0;
                case "UTILITY" -> 0.15;
                default -> 0.0;
            };
            return base * cooldownWeight;
        }).sum();
    }

    public static DifficultyComparison compare(DifficultyRating playerRating, DifficultyRating monsterRating) {
        DifficultyRating safePlayer = playerRating == null ? emptyRating() : playerRating;
        DifficultyRating safeMonster = monsterRating == null ? emptyRating() : monsterRating;
        double ratio = safeMonster.power() / Math.max(1.0, safePlayer.power());
        return new DifficultyComparison(safePlayer, safeMonster, ratio, DifficultyBand.fromRatio(ratio));
    }

    private static DifficultyRating emptyRating() {
        return new DifficultyRating(minLevel(), 1.0, 0.0, 0.0, 0.0);
    }

    private static double offenseDivisor() {
        return Math.max(1.0, GameConfiguration.doubleValue("difficulty.offenseDivisor", 8.0));
    }

    private static double survivalDivisor() {
        return Math.max(1.0, GameConfiguration.doubleValue("difficulty.survivalDivisor", 10.0));
    }

    private static double speedMultiplierCap() {
        return Math.max(1.0, GameConfiguration.doubleValue("difficulty.speedMultiplierCap", 4.0));
    }

    private static int minLevel() {
        return Math.max(1, GameConfiguration.intValue("difficulty.minimumLevel", 1));
    }

    private static long contextRevision() {
        return GameConfiguration.revision() * 31L
                + System.identityHashCode(BattleContentCatalog.current());
    }

    private static int playerSignature(PlayerCharacter player) {
        int result = player.getMaxHp();
        for (PlayerStat stat : PlayerStat.values()) {
            result = 31 * result + player.getStat(stat);
        }
        for (CharacterSkill skill : List.of(
                CharacterSkill.ATTACK,
                CharacterSkill.STRENGTH,
                CharacterSkill.DEFENSE,
                CharacterSkill.MAGIC_ACCURACY,
                CharacterSkill.MAGIC_POWER)) {
            result = 31 * result + player.getSkillLevel(skill);
        }
        return 31 * result + player.getBattleSkills().hashCode();
    }

    private record CachedRating(long contextRevision, int signature, DifficultyRating rating) {
    }

    private record ProfileKey(
            String name,
            Map<PlayerStat, Integer> stats,
            List<String> skillIds,
            long contextRevision
    ) {
        private ProfileKey {
            name = name == null ? "" : name;
            stats = stats == null ? Map.of() : Map.copyOf(stats);
            skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
        }
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
