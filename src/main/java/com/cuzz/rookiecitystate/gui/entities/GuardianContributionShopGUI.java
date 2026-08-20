package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.gui.BasePlayerGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.guardian.shop.GuardianShopProduct;
import com.cuzz.rookiecitystate.guardian.shop.GuardianShopView;
import com.cuzz.rookiecitystate.internal.item.ItemBuilder;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.util.Util;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class GuardianContributionShopGUI extends BasePlayerGUI {
    private final RookieCityState plugin = RookieCityState.inst();
    private final CityState cityState;
    private final ConfigurationSection config = plugin.getGUIYaml("GuardianContributionShopGUI");

    public GuardianContributionShopGUI(@Nullable GUI lastGUI, @NotNull CityState cityState,
                                       @NotNull CityStatePlayer player) {
        super(lastGUI, Type.GUARDIAN_SHOP, player); this.cityState = cityState;
    }

    @Override public Inventory createInventory() {
        GuardianShopView view = plugin.getGuardianContributionShopService().getView(getBukkitPlayer(), cityState);
        PlaceholderContainer placeholders = new PlaceholderContainer().add("available", view.availableContribution())
                .add("lifetime", view.lifetimeContribution()).add("remaining", remaining(view.nextRotationAt()));
        IndexConfigGUI.Builder builder = new IndexConfigGUI.Builder().fromConfig(config, getBukkitPlayer(), placeholders);
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.back"), getBukkitPlayer()), event -> back());
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.info"), getBukkitPlayer(), placeholders));
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.locker"), getBukkitPlayer()), event -> {
            close(); new GuardianCosmeticLockerGUI(this, cityState, cityStatePlayer).open();
        });
        List<Integer> slots = Util.getIndexes(config.getString("slots"));
        for (int i = 0; i < Math.min(slots.size(), view.rotation().size()); i++) {
            GuardianShopProduct product = view.rotation().get(i);
            boolean owned = view.ownedProductIds().contains(product.id());
            int bought = view.purchaseCounts().getOrDefault(product.id(), 0);
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("&8▪ &7类型：&f" + kindName(product.kind().name()));
            lore.add("&8▪ &7价格：&a" + product.price() + " 贡献");
            if (product.minimumGuardianLevel() > 0) {
                lore.add("&8▪ &7守护兽要求：&fLv." + product.minimumGuardianLevel());
            }
            lore.add("&8▪ &7本周限购：&f" + bought + "/" + product.weeklyLimit());
            lore.add("");
            lore.add(owned ? "&a✔ 已永久拥有" : "&e▶ 点击查看并购买");
            builder.item(slots.get(i), new ItemBuilder().material(product.icon()).colored()
                    .displayName(product.displayName()).lores(lore).build(), event -> {
                close(); new GuardianShopConfirmGUI(this, cityState, cityStatePlayer, product.id()).open();
            });
        }
        return builder.build();
    }

    private static String kindName(String kind) {
        return switch (kind) {
            case "PARTICLE" -> "粒子特效";
            case "TITLE" -> "守护兽称号";
            case "CHAT_PREFIX" -> "聊天前缀";
            case "ACTION" -> "互动动作";
            case "ITEM" -> "物品奖励";
            case "MAGIC_STONE" -> "魔力石";
            default -> kind;
        };
    }

    @Override public boolean canUse() {
        return cityStatePlayer.getCityState() == cityState && cityState.isWorldReady() && cityState.isValid();
    }

    private String remaining(long next) {
        long seconds = Math.max(0L, (next - System.currentTimeMillis()) / 1000L);
        long days = seconds / 86400L; seconds %= 86400L;
        long hours = seconds / 3600L;
        return days + "天" + hours + "小时";
    }
}
