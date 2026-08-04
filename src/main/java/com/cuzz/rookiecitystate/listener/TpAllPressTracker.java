package com.cuzz.rookiecitystate.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks consecutive sneak presses for the newest TpAll request of each player. */
public final class TpAllPressTracker {
    private record Counter(UUID requestId, int presses, long lastPress) { }

    private final Map<UUID, Counter> counters = new HashMap<>();

    public int press(UUID playerId, UUID requestId, long now, long intervalMillis) {
        Counter old = counters.get(playerId);
        int presses = old == null || !old.requestId().equals(requestId)
                || now - old.lastPress() > intervalMillis ? 1 : old.presses() + 1;
        counters.put(playerId, new Counter(requestId, presses, now));
        return presses;
    }

    public void remove(UUID playerId) {
        counters.remove(playerId);
    }

    public void clear() {
        counters.clear();
    }
}
