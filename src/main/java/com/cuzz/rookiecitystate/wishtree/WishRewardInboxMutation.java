package com.cuzz.rookiecitystate.wishtree;

import com.cuzz.rookiecitystate.player.CityStatePlayer;

import java.util.UUID;

/** Internal cross-module mutation facade. Caller must synchronize and persist the player exactly once. */
public final class WishRewardInboxMutation {
    private WishRewardInboxMutation() { }

    public static int remaining(CityStatePlayer player) {
        return new PlayerWishData(player).mailboxRemaining();
    }

    public static UUID enqueue(CityStatePlayer player, WishRewardDefinition reward, String source, UUID cityId) {
        return new PlayerWishData(player).enqueue(reward, source, cityId);
    }
}
