package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.CityStateIcon;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.gui.BasePageableGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.internal.item.ItemBuilder;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.social.CityPopularityEntry;
import com.cuzz.rookiecitystate.util.Util;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PopularCityStateGUI extends BasePageableGUI {
    private final RookieCityState plugin = RookieCityState.inst();
    private final ConfigurationSection config = plugin.getGUIYaml("PopularCityStateGUI");
    private final List<Integer> indexes = Util.getIndexes(config.getString("items.city.indexes"));
    private List<CityPopularityEntry> entries = List.of();

    public PopularCityStateGUI(GUI lastGUI, @NotNull CityStatePlayer player) {
        super(lastGUI, Type.POPULAR_CITY_STATE, player);
    }

    @Override public void update() {
        entries = plugin.getCitySocialService().getPopularCities();
        setPageCount(entries.isEmpty() ? 0 : (entries.size() + indexes.size() - 1) / indexes.size());
    }

    @Override public Inventory createInventory() {
        IndexConfigGUI.Builder builder = new IndexConfigGUI.Builder().fromConfig(config, getBukkitPlayer(),
                new PlaceholderContainer().add("page", getCurrentPage() + 1).add("total_page", getPageCount()));
        builder.pageItems(config.getConfigurationSection("items.page_items"), this);
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.back"), getBukkitPlayer()), event -> back());
        if (getCurrentPage() >= 0) {
            int start = getCurrentPage() * indexes.size();
            for (int i = start; i < Math.min(start + indexes.size(), entries.size()); i++) {
                CityPopularityEntry entry = entries.get(i);
                CityState city = entry.cityState();
                PlaceholderContainer values = new PlaceholderContainer().addCityStatePlaceholders(city)
                        .add("social_hot_rank", entry.rank()).add("social_hot_score", entry.hotScore())
                        .add("social_7d_visitors", entry.recentVisitors()).add("social_7d_likes", entry.recentLikes())
                        .add("social_total_likes", entry.totalLikes());
                ItemBuilder item = GUIItemManager.getItemBuilder(config.getConfigurationSection("items.city.icon"),
                        getBukkitPlayer(), values);
                CityStateIcon icon = city.getCurrentIcon();
                if (icon == null) item.material(MainSettings.getCityStateIconDefaultMaterial());
                else item.material(icon.getMaterial());
                int slot = indexes.get(i - start);
                builder.item(slot, item.build(), event -> {
                    close(); new CityStateInfoGUI(PopularCityStateGUI.this, cityStatePlayer, city).open();
                });
            }
        }
        return builder.build();
    }

    @Override public boolean canUse() { return getBukkitPlayer() != null && getBukkitPlayer().isOnline(); }
}
