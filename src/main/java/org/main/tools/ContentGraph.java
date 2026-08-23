package org.main.tools;

import java.util.List;

record ContentGraph(String selectedLabel, List<String> dependencies, List<String> references) {
    ContentGraph {
        selectedLabel = selectedLabel == null || selectedLabel.isBlank() ? "Selected Content" : selectedLabel;
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        references = references == null ? List.of() : List.copyOf(references);
    }
}

