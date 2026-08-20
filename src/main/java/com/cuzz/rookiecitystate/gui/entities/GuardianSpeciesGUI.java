package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.gui.BasePlayerGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.guardian.GuardianSpecies;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GuardianSpeciesGUI extends BasePlayerGUI {
    private final RookieCityState plugin = RookieCityState.inst();
    private final CityState cityState;
    private final ConfigurationSection config = plugin.getGUIYaml("GuardianSpeciesGUI");

    public GuardianSpeciesGUI(@Nullable GUI lastGUI, @NotNull CityState cityState, @NotNull CityStatePlayer player) {
        super(lastGUI, Type.GUARDIAN_SPECIES, player);
        this.cityState = cityState;
    }

    @Override public Inventory createInventory() {
        IndexConfigGUI.Builder builder = new IndexConfigGUI.Builder().fromConfig(config, getBukkitPlayer());
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.back"), getBukkitPlayer()), event -> back());
        for (GuardianSpecies species : GuardianSpecies.values()) {
            var item = GUIItemManager.getIndexItem(config.getConfigurationSection("items.species_" + species.id()), getBukkitPlayer());
            var definition = plugin.getGuardianBeastService().getConfig().species(species);
            item.getItemBuilder().material(definition.icon()).displayName("&f" + definition.displayName()).colored();
            builder.item(item, event -> {
                close();
                new GuardianSpeciesConfirmGUI(this, cityState, cityStatePlayer, species).open();
            });
        }
        return builder.build();
    }

    @Override public boolean canUse() {
        return cityState.isWorldReady() && cityState.isOwner(cityStatePlayer)
                && plugin.getGuardianBeastService().state(cityState).species() == null
                && plugin.getGuardianBeastService().isAvailable();
    }
}
