package org.main.content;

import java.util.List;

/**
 * Describes one handler-owned field.  The Construction Kit consumes these
 * descriptors directly, so adding a handler does not require editor code.
 */
public record AuthoringFieldDescriptor(
        String key,
        String label,
        FieldType type,
        String defaultValue,
        double minimum,
        double maximum,
        List<String> options,
        String help
) {
    public AuthoringFieldDescriptor {
        key = key == null ? "" : key;
        label = label == null || label.isBlank() ? key : label;
        type = type == null ? FieldType.TEXT : type;
        defaultValue = defaultValue == null ? "" : defaultValue;
        options = options == null ? List.of() : List.copyOf(options);
        help = help == null ? "" : help;
    }

    public static AuthoringFieldDescriptor integer(
            String key, String label, int defaultValue, int minimum, int maximum, String help) {
        return new AuthoringFieldDescriptor(
                key, label, FieldType.INTEGER, String.valueOf(defaultValue),
                minimum, maximum, List.of(), help);
    }

    public static AuthoringFieldDescriptor decimal(
            String key, String label, double defaultValue, double minimum, double maximum, String help) {
        return new AuthoringFieldDescriptor(
                key, label, FieldType.DECIMAL, String.valueOf(defaultValue),
                minimum, maximum, List.of(), help);
    }

    public static AuthoringFieldDescriptor choice(
            String key, String label, String defaultValue, List<String> options, String help) {
        return new AuthoringFieldDescriptor(
                key, label, FieldType.CHOICE, defaultValue,
                0, 0, options, help);
    }

    public static AuthoringFieldDescriptor reference(
            String key, String label, FieldType type, String help) {
        return new AuthoringFieldDescriptor(key, label, type, "", 0, 0, List.of(), help);
    }

    public enum FieldType {
        TEXT,
        INTEGER,
        DECIMAL,
        PERCENT,
        CHOICE,
        STATUS_REFERENCE,
        MOB_REFERENCE,
        ASSET_PATH
    }
}
