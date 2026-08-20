package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.gui.BaseConfirmGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.guardian.GuardianSpecies;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.util.Util;

final class GuardianSpeciesConfirmGUI extends BaseConfirmGUI {
    private final RookieCityState plugin = RookieCityState.inst();
    private final CityState cityState;
    private final GuardianSpecies species;

    GuardianSpeciesConfirmGUI(GUI lastGUI, CityState cityState, CityStatePlayer player, GuardianSpecies species) {
        super(lastGUI, player, RookieCityState.inst().getGUIYaml("GuardianSpeciesGUI").getConfigurationSection("confirm"),
                new PlaceholderContainer().add("species", RookieCityState.inst().getGuardianBeastService()
                        .getConfig().species(species).displayName()));
        this.cityState = cityState;
        this.species = species;
    }

    @Override public boolean canUse() {
        return cityState.isOwner(cityStatePlayer) && plugin.getGuardianBeastService().state(cityState).species() == null;
    }

    @Override public void onConfirm() {
        close();
        var result = plugin.getGuardianBeastService().selectSpecies(getBukkitPlayer(), cityState, species);
        Util.sendMsg(getBukkitPlayer(), result.status() == com.cuzz.rookiecitystate.guardian.SpeciesSelectionResult.Status.SUCCESS
                ? "&a" + result.message() : "&c" + result.message());
        new GuardianBeastGUI(null, cityState, cityStatePlayer).open();
    }

    @Override public void onCancel() { back(); }
}
