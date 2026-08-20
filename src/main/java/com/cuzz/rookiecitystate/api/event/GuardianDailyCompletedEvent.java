package com.cuzz.rookiecitystate.api.event;

import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class GuardianDailyCompletedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CityState cityState;
    private final CityStatePlayer completingPlayer;
    private final int completedDays;
    public GuardianDailyCompletedEvent(CityState cityState, CityStatePlayer completingPlayer, int completedDays) {
        this.cityState = cityState; this.completingPlayer = completingPlayer; this.completedDays = completedDays;
    }
    public CityState getCityState() { return cityState; }
    public CityStatePlayer getCompletingPlayer() { return completingPlayer; }
    public int getCompletedDays() { return completedDays; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
