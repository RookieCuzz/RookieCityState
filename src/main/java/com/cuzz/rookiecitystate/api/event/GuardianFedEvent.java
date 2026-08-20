package com.cuzz.rookiecitystate.api.event;

import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.guardian.FeedResult;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class GuardianFedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CityState cityState;
    private final CityStatePlayer player;
    private final FeedResult result;
    public GuardianFedEvent(CityState cityState, CityStatePlayer player, FeedResult result) {
        this.cityState = cityState; this.player = player; this.result = result;
    }
    public CityState getCityState() { return cityState; }
    public CityStatePlayer getCityStatePlayer() { return player; }
    public FeedResult getResult() { return result; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
