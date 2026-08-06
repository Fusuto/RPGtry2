package org.main.core;

import org.main.monsters.Monster;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable, saveable contents and butchery state for one defeated enemy.
 */
public final class CorpseState {
    private final Monster monster;
    private final String sourceSpawnId;
    private final List<InventorySystem.Item> contents = new ArrayList<>();
    private boolean butcheryAttempted;
    private String statusMessage = "Choose what to recover before leaving the remains.";

    public CorpseState(Monster monster, String sourceSpawnId, List<InventorySystem.Item> contents) {
        this.monster = monster;
        this.sourceSpawnId = sourceSpawnId == null ? "" : sourceSpawnId;
        if (contents != null) {
            contents.stream().filter(item -> item != null).forEach(this::append);
        }
    }

    public Monster monster() {
        return monster;
    }

    public String sourceSpawnId() {
        return sourceSpawnId;
    }

    public List<InventorySystem.Item> contents() {
        return List.copyOf(contents);
    }

    public InventorySystem.Item itemAt(int index) {
        return index < 0 || index >= contents.size() ? null : contents.get(index);
    }

    public boolean transferTo(InventorySystem.Inventory inventory, int corpseIndex, Integer targetIndex) {
        InventorySystem.Item item = itemAt(corpseIndex);
        if (inventory == null || item == null) {
            return false;
        }
        if (targetIndex != null) {
            InventorySystem.Item target = inventory.getItem(targetIndex);
            boolean compatibleStack = target != null
                    && item.isStackable()
                    && target.isStackable()
                    && (!item.getContentId().isBlank()
                            && item.getContentId().equalsIgnoreCase(target.getContentId())
                            || item.getContentId().isBlank()
                            && item.getName().equalsIgnoreCase(target.getName()));
            if (target != null && !compatibleStack) {
                return false;
            }
        }
        boolean added = targetIndex == null
                ? inventory.addItem(item)
                : inventory.addItemAt(item, targetIndex);
        if (added) {
            contents.remove(corpseIndex);
        }
        return added;
    }

    public void append(InventorySystem.Item item) {
        if (item == null) {
            return;
        }
        if (item.isStackable()) {
            for (InventorySystem.Item existing : contents) {
                boolean sameContent = !item.getContentId().isBlank()
                        ? item.getContentId().equalsIgnoreCase(existing.getContentId())
                        : item.getName().equalsIgnoreCase(existing.getName());
                if (existing.isStackable() && sameContent) {
                    existing.addQuantity(item.getQuantity());
                    return;
                }
            }
        }
        contents.add(item);
    }

    public boolean butcheryAttempted() {
        return butcheryAttempted;
    }

    public void markButcheryAttempted(String message) {
        butcheryAttempted = true;
        statusMessage = message == null ? "" : message;
    }

    public void restoreButcheryState(boolean attempted, String message) {
        butcheryAttempted = attempted;
        statusMessage = message == null ? "" : message;
    }

    public String statusMessage() {
        return statusMessage;
    }

    public boolean isEmpty() {
        return contents.isEmpty();
    }

    public CorpseState copy() {
        CorpseState copy = new CorpseState(
                monster == null ? null : monster.freshCopy(),
                sourceSpawnId,
                contents.stream().map(InventorySystem.Item::copy).toList());
        copy.restoreButcheryState(butcheryAttempted, statusMessage);
        return copy;
    }
}
