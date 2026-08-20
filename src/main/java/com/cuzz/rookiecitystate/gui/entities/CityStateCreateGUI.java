package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.DebugMessage;
import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.gui.BaseConfirmGUI;
import com.cuzz.rookiecitystate.gui.BasePlayerGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.citystate.CityStateManager;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.thirdparty.economy.PlayerPointsEconomy;
import com.cuzz.rookiecitystate.thirdparty.economy.VaultEconomy;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.placeholder.PlaceholderText;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.internal.inventory.ItemListener;
import com.cuzz.rookiecitystate.internal.text.MessageService;
import com.cuzz.rookiecitystate.internal.text.Title;
import com.cuzz.rookiecitystate.internal.text.LegacyText;
import com.cuzz.rookiecitystate.transaction.TransactionService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CityStateCreateGUI extends BasePlayerGUI {
    private final String cityStateName;
    private final Player bukkitPlayer = getBukkitPlayer();
    private final RookieCityState plugin = RookieCityState.inst();
    private final CityStateManager cityStateManager = plugin.getCityStateManager();
    private final ConfigurationSection thisGUISection = plugin.getGUIYaml("CityStateCreateGUI");
    private final ConfigurationSection thisLangSection = plugin.getLangYaml().getConfigurationSection("CityStateCreateGUI");

    private final PlayerPointsEconomy playerPointsEconomy = plugin.getPlayerPointsEconomy();
    private final VaultEconomy vaultEconomy = plugin.getVaultEconomy();

    protected CityStateCreateGUI(@Nullable GUI lastGUI, @NotNull CityStatePlayer cityStatePlayer, @NotNull String cityStateName) {
        super(lastGUI, Type.CREATE, cityStatePlayer);

        this.cityStateName = cityStateName;
    }

    /**
     * 不在城邦就允许使用
     * @return
     */
    @Override
    public boolean canUse() {
        return !cityStatePlayer.isInCityState();
    }

    @Override
    public Inventory createInventory() {
        PlaceholderContainer moneyPlaceholderContainer = new PlaceholderContainer()
                .add("name", cityStateName)
                .add("city_state_name", cityStateName)
                .add("price", MainSettings.getCityStateCreatePriceMoneyAmount())
                .add("cost", MainSettings.getCityStateCreatePriceMoneyAmount())
                .add("money_cost", MainSettings.getCityStateCreatePriceMoneyAmount());
        PlaceholderContainer pointsPlaceholderContainer = new PlaceholderContainer()
                .add("name", cityStateName)
                .add("city_state_name", cityStateName)
                .add("price", MainSettings.getCityStateCreatePricePointsAmount())
                .add("cost", MainSettings.getCityStateCreatePricePointsAmount())
                .add("points_cost", MainSettings.getCityStateCreatePricePointsAmount());

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_BASIC);

        IndexConfigGUI.Builder guiBuilder = new IndexConfigGUI.Builder()
                .fromConfig(thisGUISection, bukkitPlayer);

        PluginLogger.debug(DebugMessage.END_GUI_LOAD_BASIC);

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.money");
        guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.money"), bukkitPlayer, moneyPlaceholderContainer), new ItemListener() {
                    @Override
                    public void onClick(InventoryClickEvent event) {
                        if (!vaultEconomy.has(bukkitPlayer, MainSettings.getCityStateCreatePriceMoneyAmount())) {
                            Util.sendMsg(bukkitPlayer, PlaceholderText.replacePlaceholders(thisLangSection.getString("money.not_enough"), new PlaceholderContainer()
                                    .add("need", MainSettings.getCityStateCreatePriceMoneyAmount() - vaultEconomy.getBalance(bukkitPlayer))));
                            reopen(60L);
                            return;
                        }


                        new BaseConfirmGUI(CityStateCreateGUI.this, cityStatePlayer, thisGUISection.getConfigurationSection("items.money.ConfirmGUI"), moneyPlaceholderContainer) {
                            @Override
                            public boolean canUse() {
                                return !cityStatePlayer.isInCityState() && plugin.isVaultEconomyHooked() && vaultEconomy.has(bukkitPlayer, MainSettings.getCityStateCreatePriceMoneyAmount());
                            }

                            @Override
                            public void onConfirm() {
                                close();
                                beginCreation(cityStatePlayer, cityStateName,
                                        plugin.getTransactionService().vault(vaultEconomy, bukkitPlayer,
                                                MainSettings.getCityStateCreatePriceMoneyAmount()),
                                        "VAULT", MainSettings.getCityStateCreatePriceMoneyAmount());
                            }

                            @Override
                            public void onCancel() {
                                back();
                            }
                        }.open();
                    }
                });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.money");

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.points");
        guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.points"), bukkitPlayer, pointsPlaceholderContainer), new ItemListener() {
                    @Override
                    public void onClick(InventoryClickEvent event) {
                        if (playerPointsEconomy == null) {
                            Util.sendMsg(bukkitPlayer, "&cPlayerPoints 未启用.");
                            reopen(40L);
                            return;
                        }

                        if (!playerPointsEconomy.has(bukkitPlayer, MainSettings.getCityStateCreatePricePointsAmount())) {
                            Util.sendMsg(bukkitPlayer, PlaceholderText.replacePlaceholders(thisLangSection.getString("points.not_enough"), new PlaceholderContainer()
                                    .add("need", String.valueOf(MainSettings.getCityStateCreatePricePointsAmount() - playerPointsEconomy.getBalance(bukkitPlayer)))));
                            reopen(40L);
                            return;
                        }

                        new BaseConfirmGUI(CityStateCreateGUI.this, cityStatePlayer, plugin.getGUIYaml("CityStateCreateGUI").getConfigurationSection("items.points.ConfirmGUI"), pointsPlaceholderContainer) {
                            @Override
                            public boolean canUse() {
                                return !cityStatePlayer.isInCityState() && plugin.isPlayerPointsHooked() && playerPointsEconomy.has(bukkitPlayer, MainSettings.getCityStateCreatePricePointsAmount());
                            }

                            @Override
                            public void onConfirm() {
                                close();
                                beginCreation(cityStatePlayer, cityStateName,
                                        plugin.getTransactionService().points(playerPointsEconomy, bukkitPlayer,
                                                MainSettings.getCityStateCreatePricePointsAmount()),
                                        "PLAYER_POINTS", MainSettings.getCityStateCreatePricePointsAmount());
                            }

                            @Override
                            public void onCancel() {
                                back();
                            }
                        }.open();
                    }
                });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.points");

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.back");
        guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.back"), bukkitPlayer), new ItemListener() {
                    @Override
                    public void onClick(InventoryClickEvent event) {
                        if (canBack()) {
                            back();
                        }
                    }
                });
        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.back");

        return guiBuilder.build();
    }

    private void beginCreation(CityStatePlayer cityStatePlayer, String cityStateName,
                               TransactionService.Payment payment, String paymentType, double amount) {
        try {
            validateCreation(cityStatePlayer, cityStateName);
        } catch (RuntimeException error) {
            Util.sendMsg(bukkitPlayer, "&c创建失败: " + error.getMessage());
            return;
        }
        Util.sendMsg(bukkitPlayer, "&e城邦世界正在建设，完成前不会扣款，请勿重复提交。");
        plugin.getCityStateLifecycleService().create(cityStatePlayer, cityStateName, payment, paymentType, amount)
                .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        Util.sendMsg(bukkitPlayer, "&c创建失败: " + error.getMessage());
                    } else if (!result.success()) {
                        Util.sendMsg(bukkitPlayer, "&c创建失败: " + result.reason());
                    } else {
                        showCreationSuccess(cityStatePlayer, cityStateName);
                    }
                }));
    }

    private void showCreationSuccess(CityStatePlayer cityStatePlayer, String cityStateName) {
        Player bukkitPlayer = cityStatePlayer.getBukkitPlayer();
        PlaceholderContainer placeholderContainer = new PlaceholderContainer()
                .add("player", bukkitPlayer.getName())
                .add("city_state_name", cityStateName);

        MessageService.broadcastColoredMessage(PlaceholderText.replacePlaceholders(thisLangSection.getString("success.broadcast"), placeholderContainer));

        if (MessageService.isTitleEnabled()) {
            bukkitPlayer.sendTitle(LegacyText.getColoredText(PlaceholderText.replacePlaceholders(thisLangSection.getString("success.title"), placeholderContainer))
                    , LegacyText.getColoredText(PlaceholderText.replacePlaceholders(thisLangSection.getString("success.subtitle"), placeholderContainer)), 0, 20, 20);
        } else {
            Util.sendMsg(bukkitPlayer, thisLangSection.getString("success.msg"));
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                new MainGUI(cityStatePlayer).open();
            }
        }.runTaskLater(plugin, 20L * 3L);
    }

    private void validateCreation(CityStatePlayer player, String name) {
        if (player.isInCityState()) throw new IllegalStateException("你已经加入城邦");
        if (CityStateManager.normalizeName(name).isEmpty()) throw new IllegalArgumentException("城邦名不能为空");
        if (cityStateManager.getCityStateByName(name) != null) throw new IllegalArgumentException("城邦名已经存在");
    }
}
