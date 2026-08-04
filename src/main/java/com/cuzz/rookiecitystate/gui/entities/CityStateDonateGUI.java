package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.DebugMessage;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.gui.BaseConfirmGUI;
import com.cuzz.rookiecitystate.gui.BasePlayerGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.citystate.CityStateBank;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.thirdparty.economy.PlayerPointsEconomy;
import com.cuzz.rookiecitystate.thirdparty.economy.VaultEconomy;
import com.cuzz.rookiecitystate.internal.inventory.ItemListener;
import com.cuzz.rookiecitystate.transaction.TransactionService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

public class CityStateDonateGUI extends BasePlayerGUI {
    private enum PayType {
        MONEY, POINTS
    }

    private RookieCityState plugin = RookieCityState.inst();
    private final CityStateMember cityStateMember;
    private final CityState cityState;
    private final Player bukkitPlayer = getBukkitPlayer();
    private final ConfigurationSection thisGUISection = plugin.getGUIYaml("CityStateDonateGUI");
    private final ConfigurationSection thisLangSection = plugin.getLangYaml().getConfigurationSection("CityStateDonateGUI");
    private final PlayerPointsEconomy playerPointsEconomy = plugin.getPlayerPointsEconomy();
    private final VaultEconomy vaultEconomy = plugin.getVaultEconomy();

    protected CityStateDonateGUI(@Nullable GUI lastGUI, @NotNull CityStateMember cityStateMember) {
        super(lastGUI, Type.DONATE, cityStateMember.getCityStatePlayer());

        this.cityStateMember = cityStateMember;
        this.cityState = cityStateMember.getCityState();
    }


    @Override
    public boolean canUse() {
        return cityStateMember.isValid();
    }

    @Override
    public Inventory createInventory() {
        IndexConfigGUI.Builder guiBuilder = new IndexConfigGUI.Builder()
                .fromConfig(thisGUISection, bukkitPlayer);

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.back");
        guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.back"), bukkitPlayer), new ItemListener() {
            @Override
            public void onClick(InventoryClickEvent event) {
                if (canBack()) {
                    back();
                }
            }
        });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.back");

        if (thisGUISection.contains("donate_items")) {
            for (String itemName : thisGUISection.getConfigurationSection("donate_items").getKeys(false)) {
                ConfigurationSection itemSection = thisGUISection.getConfigurationSection("donate_items").getConfigurationSection(itemName);
                ConfigurationSection donateItemSection = itemSection.getConfigurationSection("donate");
                PayType payType;
                double price;
                double reward;
                ConfigurationSection confirmGUISection;
                try {
                    if (donateItemSection == null) throw new IllegalArgumentException("缺少 donate 配置");
                    payType = PayType.valueOf(donateItemSection.getString("pay_type", "").toUpperCase(java.util.Locale.ROOT));
                    price = donateItemSection.getDouble("price", Double.NaN);
                    reward = donateItemSection.getDouble("reward", Double.NaN);
                    confirmGUISection = donateItemSection.getConfigurationSection("ConfirmGUI");
                    if (!Double.isFinite(price) || price < 0D) throw new IllegalArgumentException("price 必须是非负数");
                    if (!Double.isFinite(reward) || reward < 0D) throw new IllegalArgumentException("reward 必须是非负数");
                    if (payType == PayType.POINTS && price != Math.rint(price)) {
                        throw new IllegalArgumentException("点券价格必须是整数");
                    }
                    if (confirmGUISection == null) throw new IllegalArgumentException("缺少 ConfirmGUI");
                    int index = itemSection.getInt("index", 0);
                    if (index <= 0 || index > thisGUISection.getInt("row", 1) * 9) {
                        throw new IllegalArgumentException("槽位越界");
                    }
                } catch (RuntimeException exception) {
                    disableInvalidDonation(itemSection, guiBuilder, exception.getMessage());
                    continue;
                }

                PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "donate_items." + itemName);
                guiBuilder.item(GUIItemManager.getIndexItem(itemSection, bukkitPlayer, new PlaceholderContainer()
                        .add("price", price)
                        .add("reward", reward)), new ItemListener() {
                    @Override
                    public void onClick(InventoryClickEvent event) {
                        if (payType == PayType.POINTS) {
                            if (playerPointsEconomy == null) {
                                Util.sendMsg(bukkitPlayer, "&cPlayerPoints 未启用.");
                                reopen(40L);
                                return;
                            }

                            if (!playerPointsEconomy.has(bukkitPlayer, (int) price)) {
                                Util.sendMsg(bukkitPlayer, thisLangSection.getString("points.not_enough"), new PlaceholderContainer()
                                        .add("need", price - playerPointsEconomy.getBalance(bukkitPlayer)));
                                reopen(40);
                                return;
                            }

                            new BaseConfirmGUI(CityStateDonateGUI.this, cityStatePlayer, confirmGUISection, new PlaceholderContainer()
                                    .add("reward", reward)
                                    .add("price", price)) {
                                @Override
                                public boolean canUse() {
                                    return cityStateMember.isValid() && playerPointsEconomy.has(bukkitPlayer, (int) price);
                                }

                                @Override
                                public void onConfirm() {
                                    performDonation(PayType.POINTS, price, reward);
                                }

                                @Override
                                public void onCancel() {
                                    if (canBack()) {
                                        back();
                                    }
                                }
                            }.open();

                            return;
                        }

                        if (payType == PayType.MONEY) {
                            if (!vaultEconomy.has(bukkitPlayer, price)) {
                                Util.sendMsg(bukkitPlayer, thisLangSection.getString("money.not_enough"), new PlaceholderContainer()
                                        .add("need", price - vaultEconomy.getBalance(bukkitPlayer)));
                                reopen(40);
                                return;
                            }

                            new BaseConfirmGUI(CityStateDonateGUI.this, cityStatePlayer, confirmGUISection, new PlaceholderContainer()
                                    .add("reward", reward)
                                    .add("price", price)) {
                                @Override
                                public boolean canUse() {
                                    return cityStateMember.isValid() && vaultEconomy.has(bukkitPlayer, price);
                                }

                                @Override
                                public void onConfirm() {
                                    performDonation(PayType.MONEY, price, reward);
                                }

                                @Override
                                public void onCancel() {
                                    if (canBack()) {
                                        back();
                                    }
                                }
                            }.open();
                        }
                    }
                });
                PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "donate_items." + itemName);
            }
        }

        return guiBuilder.build();
    }

    private void disableInvalidDonation(ConfigurationSection item, IndexConfigGUI.Builder builder, String reason) {
        PluginLogger.warning("已禁用错误的赞助商品 " + item.getCurrentPath() + ": " + reason);
        int index = item.getInt("index", 0) - 1;
        int size = thisGUISection.getInt("row", 1) * 9;
        if (index >= 0 && index < size) {
            builder.item(index, new com.cuzz.rookiecitystate.internal.item.ItemBuilder()
                    .material(Material.BARRIER).colored().displayName("&c赞助配置错误")
                    .lores(List.of("&7" + reason)).build());
        }
    }

    private void performDonation(PayType type, double price, double reward) {
        TransactionService.Payment payment = type == PayType.POINTS
                ? plugin.getTransactionService().points(playerPointsEconomy, bukkitPlayer, (int) price)
                : plugin.getTransactionService().vault(vaultEconomy, bukkitPlayer, price);
        TransactionService.Result result = plugin.getTransactionService().execute(
                "赞助城邦: " + cityState.getName(),
                () -> {
                    if (!cityStateMember.isValid()) throw new IllegalStateException("成员关系已失效");
                    if (!Double.isFinite(price) || price < 0D) throw new IllegalArgumentException("价格非法");
                    if (!Double.isFinite(reward) || reward < 0D) throw new IllegalArgumentException("奖励非法");
                    if (type == PayType.POINTS && price != Math.rint(price)) throw new IllegalArgumentException("点券价格必须是整数");
                },
                payment,
                () -> applyDonation(BigDecimal.valueOf(reward)));
        if (!result.success()) {
            Util.sendMsg(bukkitPlayer, "&c赞助失败: " + result.reason());
            return;
        }
        Util.sendMsg(bukkitPlayer, thisLangSection.getString(type == PayType.POINTS ? "points.success" : "money.success"),
                new PlaceholderContainer().add("reward", reward).add("price", price));
        close();
        new BukkitRunnable() {
            @Override public void run() { if (getLastGUI() != null) getLastGUI().open(); }
        }.runTaskLater(plugin, 40L);
    }

    private void applyDonation(BigDecimal reward) {
        BigDecimal oldBank = cityState.getCityStateBank().getBalance(CityStateBank.BalanceType.GMONEY);
        BigDecimal oldDonated = cityStateMember.getDonated(CityStateBank.BalanceType.GMONEY);
        try {
            cityState.getCityStateBank().setBalance(CityStateBank.BalanceType.GMONEY, oldBank.add(reward));
            cityStateMember.setDonated(CityStateBank.BalanceType.GMONEY, oldDonated.add(reward));
        } catch (RuntimeException exception) {
            try {
                cityState.getCityStateBank().setBalance(CityStateBank.BalanceType.GMONEY, oldBank);
                cityStateMember.setDonated(CityStateBank.BalanceType.GMONEY, oldDonated);
            } catch (RuntimeException rollback) {
                exception.addSuppressed(rollback);
            }
            throw exception;
        }
    }
}
