package com.cuzz.rookiecitystate.wishtree;

import java.util.List;
import java.util.UUID;

public record WishResult(boolean success, String reason, List<UUID> claimIds, boolean targetAwarded) {
    public static WishResult failed(String reason) { return new WishResult(false, reason, List.of(), false); }
    public static WishResult ok(List<UUID> claims, boolean targetAwarded) {
        return new WishResult(true, null, List.copyOf(claims), targetAwarded);
    }
}
