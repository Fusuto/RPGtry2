package org.main.core;

public final class Library {
    private Library() {
    }

    public enum EntityType {
        ENEMY,
        ALLY,
        NPC,
        CHEST,
        TRAP,
        ITEM
    }

    public enum TileType {
        FLOOR(false),
        WALL(true),
        DOOR_CLOSED(true),
        DOOR_OPEN(false),
        QUEST_DOOR_OPEN(false),
        QUEST_DOOR_CLOSED(true),
        FISHING_WATER(true),
        WATER(true),
        TRAP(false),
        STAIRS_DOWN(false),
        STAIRS_UP(false);

        private final boolean blocksMovement;

        TileType(boolean blocksMovement) {
            this.blocksMovement = blocksMovement;
        }

        public boolean blocksMovement() {
            return blocksMovement;
        }

        public boolean isWallLike() {
            return this == WALL || this == DOOR_CLOSED || this == QUEST_DOOR_CLOSED;
        }

        public boolean isDoor() {
            return this == DOOR_CLOSED
                    || this == DOOR_OPEN
                    || this == QUEST_DOOR_OPEN
                    || this == QUEST_DOOR_CLOSED;
        }
    }

    public enum BattleRow {
        FRONT,
        BACK
    }

    public enum BattleCommand {
        ATTACK,
        SKILL,
        ITEMS,
        RUN
    }

    public enum BattleResult {
        CONTINUE,
        VICTORY,
        DEFEAT,
        RAN
    }

    public enum BattleTargetingMode {
        NORMAL_MELEE,
        REACH_MELEE,
        RANGED,
        MAGIC
    }

    public enum SkillTargetShape {
        ENTIRE_SIDE,
        SINGLE_TARGET,
        SINGLE_COLUMN,
        SINGLE_ROW
    }

    public enum EffectType{
        DAMAGE,
        HEAL,
        DEFEND,
        SUMMON
    }
}
