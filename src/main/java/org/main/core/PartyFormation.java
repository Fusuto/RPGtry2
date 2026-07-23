package org.main.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistent six-cell party formation shared by exploration menus and battle setup. */
public final class PartyFormation {
    public enum Cell {
        FRONT_LEFT(Library.BattleRow.FRONT, 0),
        FRONT_CENTER(Library.BattleRow.FRONT, 1),
        FRONT_RIGHT(Library.BattleRow.FRONT, 2),
        BACK_LEFT(Library.BattleRow.BACK, 0),
        BACK_CENTER(Library.BattleRow.BACK, 1),
        BACK_RIGHT(Library.BattleRow.BACK, 2);

        private final Library.BattleRow row;
        private final int column;

        Cell(Library.BattleRow row, int column) {
            this.row = row;
            this.column = column;
        }

        public Library.BattleRow row() {
            return row;
        }

        public int column() {
            return column;
        }

        public boolean isBack() {
            return row == Library.BattleRow.BACK;
        }

        public Cell supportingFrontCell() {
            return switch (this) {
                case BACK_LEFT -> FRONT_LEFT;
                case BACK_CENTER -> FRONT_CENTER;
                case BACK_RIGHT -> FRONT_RIGHT;
                default -> this;
            };
        }
    }

    private final LinkedHashMap<String, Cell> assignments = new LinkedHashMap<>();

    public PartyFormation(String playerMemberId) {
        if (playerMemberId != null && !playerMemberId.isBlank()) {
            assignments.put(playerMemberId, Cell.FRONT_CENTER);
        }
    }

    public synchronized Map<String, Cell> assignments() {
        return Map.copyOf(assignments);
    }

    public synchronized Cell cellOf(String memberId) {
        return assignments.get(memberId);
    }

    public synchronized String memberAt(Cell cell) {
        if (cell == null) {
            return null;
        }
        return assignments.entrySet().stream()
                .filter(entry -> entry.getValue() == cell)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    public synchronized boolean canMove(String memberId, Cell destination) {
        if (memberId == null || !assignments.containsKey(memberId) || destination == null) {
            return false;
        }
        LinkedHashMap<String, Cell> candidate = new LinkedHashMap<>(assignments);
        String displaced = memberAt(destination);
        Cell origin = candidate.get(memberId);
        candidate.put(memberId, destination);
        if (displaced != null && !displaced.equals(memberId)) {
            candidate.put(displaced, origin);
        }
        return isValid(candidate);
    }

    public synchronized boolean move(String memberId, Cell destination) {
        if (!canMove(memberId, destination)) {
            return false;
        }
        String displaced = memberAt(destination);
        Cell origin = assignments.get(memberId);
        assignments.put(memberId, destination);
        if (displaced != null && !displaced.equals(memberId)) {
            assignments.put(displaced, origin);
        }
        return true;
    }

    public synchronized boolean swap(String firstMemberId, String secondMemberId) {
        if (firstMemberId == null || secondMemberId == null
                || !assignments.containsKey(firstMemberId)
                || !assignments.containsKey(secondMemberId)) {
            return false;
        }
        return move(firstMemberId, assignments.get(secondMemberId));
    }

    public synchronized Cell addToFirstLegalCell(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return null;
        }
        Cell existing = assignments.get(memberId);
        if (existing != null) {
            return existing;
        }
        for (Cell cell : preferredOrder()) {
            if (memberAt(cell) == null && (!cell.isBack() || memberAt(cell.supportingFrontCell()) != null)) {
                assignments.put(memberId, cell);
                return cell;
            }
        }
        return null;
    }

    public synchronized void remove(String memberId) {
        assignments.remove(memberId);
        repair(new ArrayList<>(assignments.keySet()));
    }

    public synchronized void autoArrange(List<String> rosterOrder) {
        assignments.clear();
        if (rosterOrder == null) {
            return;
        }
        for (String memberId : rosterOrder) {
            addToFirstLegalCell(memberId);
        }
    }

    /** Loads old or malformed saves and preserves every legal assignment possible. */
    public synchronized void restore(Map<String, Cell> saved, List<String> rosterOrder) {
        assignments.clear();
        if (saved != null) {
            List<String> order = rosterOrder == null ? List.of() : rosterOrder;
            for (boolean backPass : new boolean[]{false, true}) {
                for (String memberId : order) {
                    Cell cell = saved.get(memberId);
                    if (cell != null && cell.isBack() == backPass && memberAt(cell) == null
                            && (!cell.isBack() || memberAt(cell.supportingFrontCell()) != null)) {
                        assignments.put(memberId, cell);
                    }
                }
            }
        }
        repair(rosterOrder);
    }

    private void repair(List<String> rosterOrder) {
        List<String> order = rosterOrder == null ? List.of() : List.copyOf(rosterOrder);
        LinkedHashMap<String, Cell> legal = new LinkedHashMap<>();
        for (String memberId : order) {
            Cell cell = assignments.get(memberId);
            if (cell != null && !legal.containsValue(cell)
                    && (!cell.isBack() || legal.containsValue(cell.supportingFrontCell()))) {
                legal.put(memberId, cell);
            }
        }
        assignments.clear();
        assignments.putAll(legal);
        for (String memberId : order) {
            if (!assignments.containsKey(memberId)) {
                addToFirstLegalCell(memberId);
            }
        }
    }

    private static boolean isValid(Map<String, Cell> values) {
        if (values.size() != values.values().stream().distinct().count()) {
            return false;
        }
        for (Cell cell : values.values()) {
            if (cell.isBack() && !values.containsValue(cell.supportingFrontCell())) {
                return false;
            }
        }
        return true;
    }

    private static List<Cell> preferredOrder() {
        return List.of(Cell.FRONT_CENTER, Cell.FRONT_LEFT, Cell.FRONT_RIGHT,
                Cell.BACK_CENTER, Cell.BACK_LEFT, Cell.BACK_RIGHT);
    }
}
