package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.gui.BasePlayerGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.social.CitySocialView;
import com.cuzz.rookiecitystate.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

public final class CityLikeConfirmGUI extends BasePlayerGUI {
    private final RookieCityState plugin = RookieCityState.inst();
    private final CityState city;
    private final ConfigurationSection config = plugin.getGUIYaml("CityLikeConfirmGUI");

    public CityLikeConfirmGUI(@NotNull GUI lastGUI, @NotNull CityStatePlayer player, @NotNull CityState city) {
        super(lastGUI, Type.CITY_LIKE_CONFIRM, player); this.city = city;
    }

    @Override public Inventory createInventory() {
        CitySocialView view = plugin.getCitySocialService().getView(getBukkitPlayer(), city);
        PlaceholderContainer values = new PlaceholderContainer().addCityStatePlaceholders(city)
                .add("social_total_likes", view.totalLikes()).add("social_votes_remaining", view.votesRemaining());
        IndexConfigGUI.Builder builder = new IndexConfigGUI.Builder().fromConfig(config, getBukkitPlayer(), values);
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.info"), getBukkitPlayer(), values));
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.confirm"), getBukkitPlayer(), values), event -> like());
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.cancel"), getBukkitPlayer()), event -> back());
        return builder.build();
    }

    private void like() {
        close();
        plugin.getCitySocialService().like(getBukkitPlayer(), city).whenComplete((result, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) Util.sendMsg(getBukkitPlayer(), "&c点赞失败: " + error.getMessage());
                    else Util.sendMsg(getBukkitPlayer(), result.success() ? "&a点赞成功！本周还可点赞 "
                            + result.remainingVotes() + " 次。" : "&c" + result.message());
                    if (lastGUI.canUse()) lastGUI.open();
                }));
    }

    @Override public boolean canUse() {
        CitySocialView view = plugin.getCitySocialService().getView(getBukkitPlayer(), city);
        return city.isValid() && city.isWorldReady() && view.status() == CitySocialView.Status.READY
                && view.qualified() && !view.likedThisWeek() && view.votesRemaining() > 0
                && city.getMember(getBukkitPlayer().getUniqueId()) == null;
    }
}
