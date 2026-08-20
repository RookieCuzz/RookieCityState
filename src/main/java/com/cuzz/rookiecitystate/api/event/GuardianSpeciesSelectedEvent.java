package com.cuzz.rookiecitystate.api.event;

import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.guardian.GuardianSpecies;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class GuardianSpeciesSelectedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CityState cityState;
    private final CityStatePlayer player;
    private final GuardianSpecies species;
    public GuardianSpeciesSelectedEvent(CityState cityState, CityStatePlayer player, GuardianSpecies species) {
        this.cityState = cityState; this.player = player; this.species = species;
    }
    public CityState getCityState() { return cityState; }
    public CityStatePlayer getCityStatePlayer() { return player; }
    public GuardianSpecies getSpecies() { return species; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
