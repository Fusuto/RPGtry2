package org.main.tools;

import java.util.Locale;

record ContentEntry(ContentCategory category, String label, String id, String type, Object value) {
    ContentEntry {
        label = label == null || label.isBlank() ? id : label;
        id = id == null ? "" : id;
        type = type == null ? "" : type;
    }

    String key() {
        return category.name() + "|" + type + "|" + id + "|" + System.identityHashCode(value);
    }

    String searchText() {
        return (category.label() + " " + label + " " + id + " " + type)
                .toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return label;
    }
}

