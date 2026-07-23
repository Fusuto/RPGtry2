package org.main.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal persistent roster foundation. Recruitment is intentionally outside this iteration. */
public final class PartyRoster {
    public static final String PLAYER_MEMBER_ID = "player";

    public record Member(String id, String displayName, boolean playerControlled) {
        public Member {
            id = id == null ? "" : id.trim();
            displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        }
    }

    private final LinkedHashMap<String, Member> members = new LinkedHashMap<>();

    public PartyRoster(String playerName) {
        members.put(PLAYER_MEMBER_ID, new Member(PLAYER_MEMBER_ID, playerName, true));
    }

    public synchronized List<Member> members() {
        return List.copyOf(members.values());
    }

    public synchronized List<String> memberIds() {
        return new ArrayList<>(members.keySet());
    }

    public synchronized Member get(String id) {
        return members.get(id);
    }

    public synchronized boolean add(Member member) {
        if (member == null || member.id().isBlank() || members.size() >= 6 || members.containsKey(member.id())) {
            return false;
        }
        members.put(member.id(), member);
        return true;
    }

    public synchronized boolean remove(String id) {
        return id != null && !PLAYER_MEMBER_ID.equals(id) && members.remove(id) != null;
    }

    public synchronized Map<String, Member> membersById() {
        return Map.copyOf(members);
    }
}
