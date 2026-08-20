package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.gui.BasePlayerGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.guardian.shop.GuardianShopProduct;
import com.cuzz.rookiecitystate.guardian.shop.GuardianShopView;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

public final class GuardianShopConfirmGUI extends BasePlayerGUI {
    private final RookieCityState plugin = RookieCityState.inst();
    private final CityState cityState;
    private final String productId;
    private final ConfigurationSection config = plugin.getGUIYaml("GuardianShopConfirmGUI");

    public GuardianShopConfirmGUI(@NotNull GUI lastGUI, @NotNull CityState cityState,
                                  @NotNull CityStatePlayer player, @NotNull String productId) {
        super(lastGUI, Type.GUARDIAN_SHOP_CONFIRM, player); this.cityState = cityState; this.productId = productId;
    }

    @Override public Inventory createInventory() {
        GuardianShopView view = plugin.getGuardianContributionShopService().getView(getBukkitPlayer(), cityState);
        GuardianShopProduct product = view.rotation().stream().filter(item -> item.id().equals(productId)).findFirst().orElse(null);
        if (product == null) throw new IllegalStateException("商品已不在当前轮换");
        PlaceholderContainer placeholders = new PlaceholderContainer().add("product", product.displayName())
                .add("price", product.price()).add("available", view.availableContribution());
        IndexConfigGUI.Builder builder = new IndexConfigGUI.Builder().fromConfig(config, getBukkitPlayer(), placeholders);
        var productItem = GUIItemManager.getIndexItem(config.getConfigurationSection("items.product"), getBukkitPlayer(), placeholders);
        productItem.getItemBuilder().material(product.icon());
        builder.item(productItem);
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.confirm"), getBukkitPlayer()), event -> buy());
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.cancel"), getBukkitPlayer()), event -> back());
        return builder.build();
    }

    private void buy() {
        close();
        plugin.getGuardianContributionShopService().purchase(getBukkitPlayer(), cityState, productId)
                .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) Util.sendMsg(getBukkitPlayer(), "&c购买失败: " + error.getMessage());
                    else Util.sendMsg(getBukkitPlayer(), result.success() ? "&a" + result.message()
                            + "，剩余贡献 " + result.remainingContribution() : "&c" + result.message());
                    if (lastGUI.canUse()) lastGUI.open();
                }));
    }

    @Override public boolean canUse() {
        return cityStatePlayer.getCityState() == cityState && cityState.isWorldReady() && cityState.isValid();
    }
}
