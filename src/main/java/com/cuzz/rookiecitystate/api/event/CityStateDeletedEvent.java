package com.cuzz.rookiecitystate.api.event;

import com.cuzz.rookiecitystate.citystate.CityState;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CityStateDeletedEvent extends Event {
    private CityState cityState;
    private static final HandlerList handlerList = new HandlerList();

    public CityStateDeletedEvent(@NotNull CityState cityState) {
        this.cityState = cityState;
    }

    public CityState getCityState() {
        return cityState;
    }

    @Override
    public HandlerList getHandlers() {
        return handlerList;
    }

    public static HandlerList getHandlerList() {
        return handlerList;
    }
}
