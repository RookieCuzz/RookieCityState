package com.cuzz.rookiecitystate.wishtree;

public record ClaimResult(boolean success, String reason, WishClaimState state) {
    public static ClaimResult failed(String reason, WishClaimState state) { return new ClaimResult(false, reason, state); }
    public static ClaimResult ok() { return new ClaimResult(true, null, WishClaimState.CLAIMED); }
}
