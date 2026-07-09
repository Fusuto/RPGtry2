package org.main.core;

import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.Map;

public class InputBindings {
    public enum Action {
        MOVE_FORWARD("Move Forward", KeyEvent.VK_W),
        MOVE_BACKWARD("Move Backward", KeyEvent.VK_S),
        STRAFE_LEFT("Strafe Left", KeyEvent.VK_A),
        STRAFE_RIGHT("Strafe Right", KeyEvent.VK_D),
        TURN_LEFT("Turn Left", KeyEvent.VK_Q),
        TURN_RIGHT("Turn Right", KeyEvent.VK_E),
        INTERACT("Interact", KeyEvent.VK_F),
        INVENTORY("Inventory", KeyEvent.VK_I),
        SKILLS("Skills", KeyEvent.VK_K),
        ESCAPE_MENU("Escape Menu", KeyEvent.VK_ESCAPE);

        private final String label;
        private final int defaultKeyCode;

        Action(String label, int defaultKeyCode) {
            this.label = label;
            this.defaultKeyCode = defaultKeyCode;
        }

        public String getLabel() {
            return label;
        }

        public int getDefaultKeyCode() {
            return defaultKeyCode;
        }
    }

    private final Map<Action, Integer> keyCodes = new EnumMap<>(Action.class);

    public InputBindings() {
        for (Action action : Action.values()) {
            keyCodes.put(action, action.getDefaultKeyCode());
        }
    }

    public boolean matches(Action action, int keyCode) {
        return getKeyCode(action) == keyCode;
    }

    public int getKeyCode(Action action) {
        return keyCodes.getOrDefault(action, action.getDefaultKeyCode());
    }

    public String getKeyText(Action action) {
        return KeyEvent.getKeyText(getKeyCode(action));
    }

    public void assignKey(Action action, int keyCode) {
        if (action == null || keyCode == KeyEvent.VK_UNDEFINED) {
            return;
        }

        for (Action existingAction : Action.values()) {
            if (existingAction != action && getKeyCode(existingAction) == keyCode) {
                keyCodes.put(existingAction, KeyEvent.VK_UNDEFINED);
            }
        }

        keyCodes.put(action, keyCode);
    }
}
