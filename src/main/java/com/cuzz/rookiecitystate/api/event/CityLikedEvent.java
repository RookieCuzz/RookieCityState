package com.cuzz.rookiecitystate.api.event;

import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.social.CityLikeResult;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class CityLikedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CityState cityState;
    private final CityStatePlayer visitor;
    private final CityLikeResult result;

    public CityLikedEvent(CityState cityState, CityStatePlayer visitor, CityLikeResult result) {
        this.cityState = cityState;
        this.visitor = visitor;
        this.result = result;
    }

    public CityState getCityState() { return cityState; }
    public CityStatePlayer getVisitor() { return visitor; }
    public CityLikeResult getResult() { return result; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
