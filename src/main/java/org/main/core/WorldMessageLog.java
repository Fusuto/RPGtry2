package org.main.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WorldMessageLog {
    public static final int MAX_ENTRIES = 200;
    public static final long RECENT_LIFETIME_MS = 8_000L;

    public enum Category {
        SUCCESS,
        FAILURE,
        WARNING,
        SYSTEM,
        SPEECH
    }

    public record Message(
            long sequence,
            long createdAtMs,
            Category category,
            String text,
            String speakerName,
            Integer worldX,
            Integer worldY,
            int repeatCount
    ) {
        public String displayText() {
            String prefix = category == Category.SPEECH && !speakerName.isBlank()
                    ? speakerName + ": "
                    : "";
            String suffix = repeatCount > 1 ? " ×" + repeatCount : "";
            return prefix + text + suffix;
        }

        public boolean hasWorldSource() {
            return worldX != null && worldY != null;
        }
    }

    private final List<Message> entries = new ArrayList<>();
    private long elapsedMs;
    private long nextSequence = 1L;

    public void advance(int deltaMs) {
        elapsedMs += Math.max(0, deltaMs);
    }

    public Message post(Category category, String text) {
        return post(category, text, "", null, null);
    }

    public Message postSpeech(String speakerName, int worldX, int worldY, String text) {
        return post(Category.SPEECH, text, speakerName, worldX, worldY);
    }

    public Message post(
            Category category,
            String text,
            String speakerName,
            Integer worldX,
            Integer worldY
    ) {
        Category safeCategory = category == null ? Category.SYSTEM : category;
        String safeText = text == null ? "" : text.trim();
        String safeSpeaker = speakerName == null ? "" : speakerName.trim();
        if (safeText.isBlank()) {
            return null;
        }

        if (!entries.isEmpty()) {
            Message previous = entries.get(entries.size() - 1);
            if (previous.category() == safeCategory
                    && previous.text().equals(safeText)
                    && previous.speakerName().equals(safeSpeaker)
                    && Objects.equals(previous.worldX(), worldX)
                    && Objects.equals(previous.worldY(), worldY)) {
                Message repeated = new Message(
                        previous.sequence(),
                        elapsedMs,
                        previous.category(),
                        previous.text(),
                        previous.speakerName(),
                        previous.worldX(),
                        previous.worldY(),
                        previous.repeatCount() + 1
                );
                entries.set(entries.size() - 1, repeated);
                return repeated;
            }
        }

        Message message = new Message(
                nextSequence++,
                elapsedMs,
                safeCategory,
                safeText,
                safeSpeaker,
                worldX,
                worldY,
                1
        );
        entries.add(message);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
        return message;
    }

    public List<Message> entries() {
        return List.copyOf(entries);
    }

    public List<Message> recent(int maximum) {
        int safeMaximum = Math.max(0, maximum);
        List<Message> recent = entries.stream()
                .filter(message -> elapsedMs - message.createdAtMs() <= RECENT_LIFETIME_MS)
                .toList();
        int fromIndex = Math.max(0, recent.size() - safeMaximum);
        return List.copyOf(recent.subList(fromIndex, recent.size()));
    }

    public long ageMs(Message message) {
        return message == null ? Long.MAX_VALUE : Math.max(0L, elapsedMs - message.createdAtMs());
    }

    public void clear() {
        entries.clear();
        elapsedMs = 0L;
        nextSequence = 1L;
    }
}
