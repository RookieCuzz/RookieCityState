package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.gui.BasePageableGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.internal.item.ItemBuilder;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.wishtree.WishQuality;
import com.cuzz.rookiecitystate.wishtree.WishRewardDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class WishTargetGUI extends BasePageableGUI {
    private static final int PAGE_SIZE = 28;
    private final RookieCityState plugin = RookieCityState.inst();
    private final CityStateMember member;
    private final ConfigurationSection config = plugin.getGUIYaml("WishTargetGUI");
    private List<WishRewardDefinition> targets = List.of();

    public WishTargetGUI(@Nullable GUI lastGUI, @NotNull CityStateMember member) {
        super(lastGUI, Type.WISH_TARGET, member.getCityStatePlayer()); this.member = member;
    }

    @Override public void update() {
        int level = plugin.getWishTreeService().getStore().get(member.getCityState()).getLevel();
        targets = plugin.getWishTreeService().getCatalog().targets(level);
        setPageCount((targets.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override public Inventory createInventory() {
        PlaceholderContainer page = new PlaceholderContainer()
                .add("page", getCurrentPage() < 0 ? 0 : getCurrentPage() + 1)
                .add("total_page", getPageCount());
        IndexConfigGUI.Builder builder = new IndexConfigGUI.Builder().fromConfig(config, getBukkitPlayer(), page);
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.back"), getBukkitPlayer()), event -> back());
        if (hasPreciousPage()) builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.previous"), getBukkitPlayer()), event -> previousPage());
        if (hasNextPage()) builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.next"), getBukkitPlayer()), event -> nextPage());
        List<Integer> slots = Util.getIndexes(config.getString("indexes"));
        if (getCurrentPage() >= 0) {
            int start = getCurrentPage() * PAGE_SIZE;
            for (int i = start; i < Math.min(start + PAGE_SIZE, targets.size()); i++) {
                WishRewardDefinition reward = targets.get(i);
                Material icon = reward.quality() == WishQuality.EPIC ? Material.NETHER_STAR : Material.DIAMOND;
                builder.item(slots.get(i - start), new ItemBuilder().material(icon).colored()
                        .displayName(reward.displayName()).lores(List.of("",
                                "&8▪ &7品质：&f" + qualityName(reward.quality()),
                                "&8▪ &7解锁等级：&f许愿树 Lv." + reward.minimumTreeLevel(),
                                "&8▪ &7保底上限：&f" + plugin.getWishTreeService().getCatalog().pityLimit(reward.quality()) + " 次",
                                "&8▪ &7提前命中：&f" + String.format(java.util.Locale.ROOT, "%.2f%%",
                                        plugin.getWishTreeService().getCatalog().earlyChance(reward.quality()) * 100D),
                                "", "&e▶ 点击设为当前心愿")).build(), event -> {
                    var result = plugin.getWishTreeService().selectTarget(getBukkitPlayer(), reward.id());
                    Util.sendMsg(getBukkitPlayer(), result.success() ? "&a已选择心愿: " + reward.displayName() : "&c" + result.reason());
                    back();
                });
            }
        }
        return builder.build();
    }
    private static String qualityName(WishQuality quality) {
        return switch (quality) {
            case COMMON -> "普通";
            case RARE -> "&b稀有";
            case EPIC -> "&d史诗";
        };
    }
    @Override public boolean canUse() { return member.isValid() && member.getCityState().isWorldReady(); }
}
