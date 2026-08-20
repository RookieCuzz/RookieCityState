package com.cuzz.rookiecitystate.wishtree;

import java.util.List;

public record WishRewardAction(
        WishRewardType type,
        String material,
        double amount,
        List<String> commands
) {
    public WishRewardAction {
        commands = commands == null ? List.of() : List.copyOf(commands);
    }
}
