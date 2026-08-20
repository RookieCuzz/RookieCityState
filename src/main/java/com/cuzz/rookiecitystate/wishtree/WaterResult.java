package com.cuzz.rookiecitystate.wishtree;

import java.util.Set;

public record WaterResult(boolean success, String reason, int growth, int target, Set<Integer> newlyUnlocked) {
    public static WaterResult failed(String reason) { return new WaterResult(false, reason, 0, 0, Set.of()); }
    public static WaterResult ok(int growth, int target, Set<Integer> unlocked) {
        return new WaterResult(true, null, growth, target, Set.copyOf(unlocked));
    }
}
