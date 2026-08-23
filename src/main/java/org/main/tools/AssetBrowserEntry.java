package org.main.tools;

import java.util.Locale;

record AssetBrowserEntry(String assetPath, AssetBrowserType type, String origin) {
    String searchText() {
        return (assetPath + " " + type.label() + " " + origin).toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return type.label() + ": " + assetPath + " [" + origin + "]";
    }
}

