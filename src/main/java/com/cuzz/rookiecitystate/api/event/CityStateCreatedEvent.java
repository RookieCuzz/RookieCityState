package com.cuzz.rookiecitystate.api.event;

import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CityStateCreatedEvent extends Event {
    private CityState cityState;
    private CityStatePlayer cityStatePlayer;
    private static final HandlerList handlerList = new HandlerList();

    public CityStateCreatedEvent(@NotNull CityState cityState, @NotNull CityStatePlayer cityStatePlayer) {
        this.cityState = cityState;
        this.cityStatePlayer = cityStatePlayer;
    }

    public CityState getCityState() {
        return cityState;
    }

    public CityStatePlayer getCityStatePlayer() {
        return cityStatePlayer;
    }

    @Override
    public HandlerList getHandlers() {
        return handlerList;
    }

    public static HandlerList getHandlerList() {
        return handlerList;
    }
}
