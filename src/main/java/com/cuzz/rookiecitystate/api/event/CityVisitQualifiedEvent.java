package com.cuzz.rookiecitystate.api.event;

import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class CityVisitQualifiedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CityState cityState;
    private final CityStatePlayer visitor;

    public CityVisitQualifiedEvent(CityState cityState, CityStatePlayer visitor) {
        this.cityState = cityState;
        this.visitor = visitor;
    }

    public CityState getCityState() { return cityState; }
    public CityStatePlayer getVisitor() { return visitor; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
