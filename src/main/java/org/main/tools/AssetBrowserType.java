package org.main.tools;

enum AssetBrowserType {
    ALL("All"),
    IMAGES("Images"),
    SOUNDS("Sounds"),
    MODELS("3D Models"),
    DATA("Data"),
    OTHER("Other");

    private final String label;

    AssetBrowserType(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}

