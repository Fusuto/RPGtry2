package org.main.engine;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared pixel-width-aware text wrapping for runtime and editor presentation.
 */
public final class TextWrapping {
    private TextWrapping() {
    }

    public static List<String> wrap(FontMetrics metrics, String text, int maxWidth) {
        return wrap(metrics, text, maxWidth, false);
    }

    public static List<String> wrapOrBlankLine(FontMetrics metrics, String text, int maxWidth) {
        return wrap(metrics, text, maxWidth, true);
    }

    private static List<String> wrap(FontMetrics metrics, String text, int maxWidth, boolean blankLine) {
        if (text == null || text.isBlank()) {
            return blankLine ? List.of("") : List.of();
        }
        int width = Math.max(1, maxWidth);
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R", -1)) {
            if (paragraph.isBlank()) {
                lines.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (metrics.stringWidth(candidate) <= width) {
                    current = new StringBuilder(candidate);
                    continue;
                }
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                if (metrics.stringWidth(word) <= width) {
                    current.append(word);
                } else {
                    splitLongWord(metrics, lines, current, word, width);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
        }
        return List.copyOf(lines);
    }

    private static void splitLongWord(
            FontMetrics metrics,
            List<String> lines,
            StringBuilder remainder,
            String word,
            int width
    ) {
        StringBuilder segment = new StringBuilder();
        for (int offset = 0; offset < word.length(); ) {
            int codePoint = word.codePointAt(offset);
            String characters = new String(Character.toChars(codePoint));
            if (!segment.isEmpty() && metrics.stringWidth(segment + characters) > width) {
                lines.add(segment.toString());
                segment.setLength(0);
            }
            segment.append(characters);
            offset += Character.charCount(codePoint);
        }
        remainder.append(segment);
    }
}
