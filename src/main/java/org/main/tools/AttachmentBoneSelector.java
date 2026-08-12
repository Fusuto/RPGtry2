package org.main.tools;

import org.main.content.FirstPersonCombatLibrary;
import org.main.experimental.LwjglSkinnedModel;

import javax.swing.JComboBox;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Searchable selector that preserves unavailable authored skeleton-node names. */
final class AttachmentBoneSelector extends JComboBox<AttachmentBoneSelector.Option> {
    record Option(
            String boneName,
            FirstPersonCombatLibrary.WieldHand inheritedHand,
            String label,
            boolean unavailable
    ) {
        boolean inherited() { return inheritedHand != null; }
        @Override public String toString() { return label; }
    }

    AttachmentBoneSelector() {
        setEditable(true);
    }

    void populate(
            FirstPersonCombatLibrary.RigDefinition rig,
            List<LwjglSkinnedModel.SkeletonNodeMetadata> source,
            FirstPersonCombatLibrary.WieldHand inheritedHand,
            String authoredBone
    ) {
        String retainedText = authoredBone == null ? "" : authoredBone.trim();
        removeAllItems();
        addItem(new Option("", FirstPersonCombatLibrary.WieldHand.RIGHT,
                "Inherit Right Hand [" + rig.rightHandBone() + "]", false));
        addItem(new Option("", FirstPersonCombatLibrary.WieldHand.LEFT,
                "Inherit Left Hand [" + rig.leftHandBone() + "]", false));
        List<LwjglSkinnedModel.SkeletonNodeMetadata> nodes = new ArrayList<>(
                source == null ? List.of() : source);
        nodes.sort(Comparator
                .comparingInt((LwjglSkinnedModel.SkeletonNodeMetadata node) ->
                        node.weightedBone() ? 0 : node.animatedNode() ? 1
                                : node.socketOrHelper() ? 2 : node.meshNode() ? 3 : 4)
                .thenComparing(LwjglSkinnedModel.SkeletonNodeMetadata::name,
                        String.CASE_INSENSITIVE_ORDER));
        for (LwjglSkinnedModel.SkeletonNodeMetadata node : nodes) {
            addItem(new Option(node.name(), null,
                    node.displayLabel() + (node.weightedBone() || node.animatedNode()
                            ? "" : " (Advanced)"), false));
        }
        Option match = retainedText.isBlank()
                ? inheritedOption(inheritedHand) : explicitOption(retainedText);
        if (match == null && !retainedText.isBlank()) {
            match = new Option(retainedText, null, retainedText + " [Unavailable]", true);
            addItem(match);
        }
        if (match != null) setSelectedItem(match);
    }

    String attachmentBone() {
        Object value = getEditor().getItem();
        if (value instanceof Option option) return option.inherited() ? "" : option.boneName();
        String text = value == null ? "" : value.toString().trim();
        for (int index = 0; index < getItemCount(); index++) {
            Option option = getItemAt(index);
            if (option.toString().equalsIgnoreCase(text)
                    || (!option.boneName().isBlank() && option.boneName().equalsIgnoreCase(text))) {
                return option.inherited() ? "" : option.boneName();
            }
        }
        return text;
    }

    FirstPersonCombatLibrary.WieldHand inheritedHand() {
        Object value = getSelectedItem();
        return value instanceof Option option ? option.inheritedHand() : null;
    }

    void selectExplicit(String boneName) {
        Option match = explicitOption(boneName == null ? "" : boneName);
        if (match == null && boneName != null && !boneName.isBlank()) {
            match = new Option(boneName.trim(), null, boneName.trim() + " [Unavailable]", true);
            addItem(match);
        }
        if (match != null) setSelectedItem(match);
    }

    private Option inheritedOption(FirstPersonCombatLibrary.WieldHand side) {
        for (int index = 0; index < getItemCount(); index++) {
            Option option = getItemAt(index);
            if (option.inheritedHand() == side) return option;
        }
        return null;
    }

    private Option explicitOption(String name) {
        for (int index = 0; index < getItemCount(); index++) {
            Option option = getItemAt(index);
            if (!option.inherited() && option.boneName().equalsIgnoreCase(name.trim())) return option;
        }
        return null;
    }
}
