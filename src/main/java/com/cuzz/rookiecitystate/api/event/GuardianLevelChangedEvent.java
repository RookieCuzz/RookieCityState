package com.cuzz.rookiecitystate.api.event;

import com.cuzz.rookiecitystate.citystate.CityState;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class GuardianLevelChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CityState cityState;
    private final int previousLevel;
    private final int newLevel;
    public GuardianLevelChangedEvent(CityState cityState, int previousLevel, int newLevel) {
        this.cityState = cityState; this.previousLevel = previousLevel; this.newLevel = newLevel;
    }
    public CityState getCityState() { return cityState; }
    public int getPreviousLevel() { return previousLevel; }
    public int getNewLevel() { return newLevel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
