package com.cuzz.rookiecitystate.wishtree;

import java.util.List;

public record WishRewardDefinition(
        String id,
        String displayName,
        WishQuality quality,
        boolean targetable,
        int minimumTreeLevel,
        double weight,
        List<WishRewardAction> actions
) {
    public WishRewardDefinition {
        actions = List.copyOf(actions);
    }
}
