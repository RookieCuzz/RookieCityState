package com.cuzz.rookiecitystate.wishtree;

public record TargetResult(boolean success, String reason, String rewardId) {
    public static TargetResult failed(String reason) { return new TargetResult(false, reason, null); }
    public static TargetResult ok(String rewardId) { return new TargetResult(true, null, rewardId); }
}
