package com.cuzz.rookiecitystate.thirdparty;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlaceholderAPIExpansion extends PlaceholderExpansion implements IntegrationHandle {
    private final RookieCityState plugin = RookieCityState.inst();

    @Override public @NotNull String getIdentifier() { return "rookiecitystate"; }
    @Override public @NotNull String getAuthor() { return "July_ss"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return Bukkit.getPluginManager().isPluginEnabled("RookieCityState"); }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return null;
        if (params.equalsIgnoreCase("is_in_city_state")) {
            return String.valueOf(plugin.getPlaceholderSnapshotService().isInCityState(player.getUniqueId()));
        }
        if (!plugin.getPlaceholderSnapshotService().isInCityState(player.getUniqueId())) {
            return MainSettings.getCityStatePapiNonStr();
        }
        return plugin.getPlaceholderSnapshotService().get(player.getUniqueId(), params);
    }

    @Override public void close() { unregister(); }
}
