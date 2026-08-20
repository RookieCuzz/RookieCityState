package com.cuzz.rookiecitystate.api.event;

import com.cuzz.rookiecitystate.citystate.CityState;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class WishTreeLevelChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CityState cityState;
    private final int previousLevel;
    private final int level;

    public WishTreeLevelChangedEvent(CityState cityState, int previousLevel, int level) {
        this.cityState = cityState;
        this.previousLevel = previousLevel;
        this.level = level;
    }

    public CityState getCityState() { return cityState; }
    public int getPreviousLevel() { return previousLevel; }
    public int getLevel() { return level; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
