package com.cuzz.rookiecitystate.api.event;

import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.wishtree.WishResult;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class WishCompletedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CityState cityState;
    private final CityStatePlayer player;
    private final WishResult result;

    public WishCompletedEvent(CityState cityState, CityStatePlayer player, WishResult result) {
        this.cityState = cityState;
        this.player = player;
        this.result = result;
    }

    public CityState getCityState() { return cityState; }
    public CityStatePlayer getCityStatePlayer() { return player; }
    public WishResult getResult() { return result; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
