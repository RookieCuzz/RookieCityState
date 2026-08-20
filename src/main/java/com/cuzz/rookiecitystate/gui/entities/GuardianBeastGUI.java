package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.gui.BasePlayerGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.guardian.GuardianBeastView;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import com.cuzz.rookiecitystate.util.Util;

public final class GuardianBeastGUI extends BasePlayerGUI {
    private final RookieCityState plugin = RookieCityState.inst();
    private final CityState cityState;
    private final ConfigurationSection config = plugin.getGUIYaml("GuardianBeastGUI");

    public GuardianBeastGUI(@Nullable GUI lastGUI, @NotNull CityState cityState, @NotNull CityStatePlayer viewer) {
        super(lastGUI, Type.GUARDIAN_BEAST, viewer);
        this.cityState = cityState;
    }

    @Override public Inventory createInventory() {
        GuardianBeastView view = plugin.getGuardianBeastService().getView(getBukkitPlayer(), cityState);
        String favorites = view.favoriteFoods().stream().map(GuardianBeastGUI::foodName).collect(Collectors.joining("、"));
        String ranking = view.dailyContributions().entrySet().stream()
                .sorted(java.util.Map.Entry.<java.util.UUID, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(5).map(entry -> plugin.getCityStatePlayerManager().getCityStatePlayer(entry.getKey()).getName()
                        + " " + entry.getValue()).collect(Collectors.joining(" / "));
        if (ranking.isBlank()) ranking = "暂无参与者";
        PlaceholderContainer placeholders = new PlaceholderContainer()
                .add("species", view.speciesName()).add("form", formName(view.form())).add("level", view.level())
                .add("days", view.completedDays()).add("fullness", view.fullness()).add("target", view.target())
                .add("favorites", favorites).add("feeds_used", view.feedsUsed()).add("feeds_remaining", view.feedsRemaining())
                .add("available", view.availableContribution()).add("lifetime", view.lifetimeContribution())
                .add("ranking", ranking).add("visual", view.visualAvailable() ? "已就绪" : "不可用");
        IndexConfigGUI.Builder builder = new IndexConfigGUI.Builder().fromConfig(config, getBukkitPlayer(), placeholders);
        if (lastGUI != null) builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.back"), getBukkitPlayer()), event -> back());
        var infoItem = GUIItemManager.getIndexItem(config.getConfigurationSection("items.info"), getBukkitPlayer(), placeholders);
        if (view.species() != null) infoItem.getItemBuilder().material(
                plugin.getGuardianBeastService().getConfig().species(view.species()).icon());
        builder.item(infoItem);
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.favorites"), getBukkitPlayer(), placeholders));
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.contribution"), getBukkitPlayer(), placeholders));
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.ranking"), getBukkitPlayer(), placeholders));
        if (cityState.getMember(getBukkitPlayer().getUniqueId()) != null
                && cityStatePlayer.getCityState() == cityState) {
            builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.shop"), getBukkitPlayer()), event -> {
                close(); new GuardianContributionShopGUI(this, cityState, cityStatePlayer).open();
            });
            builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.locker"), getBukkitPlayer()), event -> {
                close(); new GuardianCosmeticLockerGUI(this, cityState, cityStatePlayer).open();
            });
            builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.action"), getBukkitPlayer()), event ->
                    plugin.getGuardianContributionShopService().playEquippedAction(getBukkitPlayer(), cityState)
                            .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () ->
                                    Util.sendMsg(getBukkitPlayer(), error == null
                                            ? (result.success() ? "&a" : "&c") + result.message()
                                            : "&c动作失败: " + error.getMessage()))));
        }
        if (view.species() == null && cityState.isOwner(cityStatePlayer)) {
            builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.select"), getBukkitPlayer()), event -> {
                close();
                new GuardianSpeciesGUI(this, cityState, cityStatePlayer).open();
            });
        }
        return builder.build();
    }

    @Override public boolean canUse() {
        return cityState.isWorldReady() && plugin.getGuardianBeastService().isAvailable();
    }

    private static String formName(com.cuzz.rookiecitystate.guardian.GuardianForm form) {
        return switch (form) { case EGG -> "蛋"; case BABY -> "幼体"; case ADULT -> "成年体"; };
    }

    private static String foodName(Material material) {
        return switch (material) {
            case COD -> "鳕鱼"; case SALMON -> "鲑鱼"; case TROPICAL_FISH -> "热带鱼";
            case PUFFERFISH -> "河豚"; case COOKED_COD -> "熟鳕鱼"; case COOKED_SALMON -> "熟鲑鱼";
            default -> material.name();
        };
    }
}
