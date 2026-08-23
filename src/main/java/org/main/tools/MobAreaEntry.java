package org.main.tools;

record MobAreaEntry(String areaId) {
    @Override
    public String toString() {
        return areaId;
    }
}

