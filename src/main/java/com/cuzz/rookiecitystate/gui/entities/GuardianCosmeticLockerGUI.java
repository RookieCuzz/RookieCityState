package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.gui.BasePageableGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.guardian.shop.GuardianCosmeticSlot;
import com.cuzz.rookiecitystate.guardian.shop.GuardianShopProduct;
import com.cuzz.rookiecitystate.internal.item.ItemBuilder;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.util.Util;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class GuardianCosmeticLockerGUI extends BasePageableGUI {
    private static final int PAGE_SIZE = 28;
    private final RookieCityState plugin = RookieCityState.inst();
    private final CityState cityState;
    private final ConfigurationSection config = plugin.getGUIYaml("GuardianCosmeticLockerGUI");
    private List<GuardianShopProduct> products = List.of();
    private Map<GuardianCosmeticSlot, String> equipped = Map.of();

    public GuardianCosmeticLockerGUI(@NotNull GUI lastGUI, @NotNull CityState cityState,
                                     @NotNull CityStatePlayer player) {
        super(lastGUI, Type.GUARDIAN_LOCKER, player); this.cityState = cityState;
    }

    @Override public void update() {
        products = plugin.getGuardianContributionShopService().owned(cityStatePlayer).values().stream()
                .filter(GuardianShopProduct::permanent)
                .sorted(Comparator.comparing((GuardianShopProduct product) -> product.kind().ordinal())
                        .thenComparing(GuardianShopProduct::id)).toList();
        equipped = plugin.getGuardianContributionShopService().getView(getBukkitPlayer(), cityState).equipped();
        setPageCount((products.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override public Inventory createInventory() {
        PlaceholderContainer page = new PlaceholderContainer()
                .add("page", getCurrentPage() < 0 ? 0 : getCurrentPage() + 1)
                .add("total_page", getPageCount());
        IndexConfigGUI.Builder builder = new IndexConfigGUI.Builder().fromConfig(config, getBukkitPlayer(), page);
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.back"), getBukkitPlayer()), event -> back());
        if (hasPreciousPage()) builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.previous"), getBukkitPlayer()), event -> previousPage());
        if (hasNextPage()) builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.next"), getBukkitPlayer()), event -> nextPage());
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.clear"), getBukkitPlayer()), event -> clear());
        List<Integer> slots = Util.getIndexes(config.getString("indexes"));
        if (getCurrentPage() >= 0) {
            int start = getCurrentPage() * PAGE_SIZE;
            for (int i = start; i < Math.min(start + PAGE_SIZE, products.size()); i++) {
                GuardianShopProduct product = products.get(i);
                boolean active = product.id().equals(equipped.get(product.slot()));
                builder.item(slots.get(i - start), new ItemBuilder().material(product.icon()).colored()
                        .displayName(product.displayName()).lores(List.of("",
                                "&8▪ &7装扮槽位：&f" + slotName(product.slot()), "",
                                active ? "&a✔ 当前已装备 · 点击卸下" : "&e▶ 点击装备")).build(), event -> toggle(product, active));
            }
        }
        return builder.build();
    }

    private static String slotName(GuardianCosmeticSlot slot) {
        return switch (slot) {
            case PARTICLE -> "粒子特效";
            case TITLE -> "守护兽称号";
            case CHAT_PREFIX -> "聊天前缀";
            case ACTION -> "互动动作";
        };
    }

    private void toggle(GuardianShopProduct product, boolean active) {
        try {
            if (active) plugin.getGuardianContributionShopService().unequip(getBukkitPlayer(), product.slot());
            else plugin.getGuardianContributionShopService().equip(getBukkitPlayer(), product.slot(), product.id());
            Util.sendMsg(getBukkitPlayer(), active ? "&a已卸下该装扮。" : "&a已装备该装扮。");
        } catch (RuntimeException error) { Util.sendMsg(getBukkitPlayer(), "&c操作失败: " + error.getMessage()); }
        reopen();
    }

    private void clear() {
        for (GuardianCosmeticSlot slot : GuardianCosmeticSlot.values()) {
            try { plugin.getGuardianContributionShopService().unequip(getBukkitPlayer(), slot); }
            catch (RuntimeException ignored) { }
        }
        Util.sendMsg(getBukkitPlayer(), "&a已卸下全部灵兽装扮。");
        reopen();
    }

    @Override public boolean canUse() {
        return cityStatePlayer.getCityState() == cityState && cityState.isWorldReady() && cityState.isValid();
    }
}
