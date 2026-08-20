package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.DebugMessage;
import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.LangHelper;
import com.cuzz.rookiecitystate.config.Shop;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.gui.BasePlayerGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.gui.ShopItemConfirmGUI;
import com.cuzz.rookiecitystate.citystate.*;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.citystate.member.CityStatePermission;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.placeholder.PlaceholderText;
import com.cuzz.rookiecitystate.request.entities.TpAllRequest;
import com.cuzz.rookiecitystate.request.Request;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.internal.inventory.ItemListener;
import com.cuzz.rookiecitystate.internal.text.MessageService;
import com.cuzz.rookiecitystate.internal.text.LegacyText;
import com.cuzz.rookiecitystate.internal.text.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public class CityStateShopGUI extends BasePlayerGUI {
    private enum RewardType {
        CITY_STATE_SET_SPAWN, CITY_STATE_UPGRADE, CITY_STATE_TP_ALL, CITY_STATE_SHOP, CITY_STATE_ICON, BACK, COMMANDS, NONE
    }

    private CityStateMember cityStateMember;
    private String shopName;
    private YamlConfiguration yml;
    private CityState cityState;
    private RookieCityState plugin = RookieCityState.inst();
    private ConfigurationSection tpAllLangSection = plugin.getLangYaml().getConfigurationSection("TpAll");
    private ConfigurationSection thisLangSection = plugin.getLangYaml().getConfigurationSection("Shop");
    private Player bukkitPlayer = getBukkitPlayer();

    /**
     * 使用引导Shop YAML
     * @param lastGUI
     * @param cityStateMember
     */
    protected CityStateShopGUI(@Nullable GUI lastGUI, @NotNull CityStateMember cityStateMember) {
        this(lastGUI, cityStateMember, Optional.ofNullable(RookieCityState.inst().getShop(MainSettings.getCityStateShopLauncher())).orElseThrow(() -> new RuntimeException("引导商店不存在")));
    }

    protected CityStateShopGUI(@Nullable GUI lastGUI, @NotNull CityStateMember cityStateMember, @NotNull Shop shop) {
        super(lastGUI, Type.SHOP, cityStateMember.getCityStatePlayer());

        this.cityStateMember = cityStateMember;
        this.yml = shop.getYaml();
        this.shopName = shop.getName();
        this.cityState = cityStateMember.getCityState();
    }

    @Override
    public boolean canUse() {
        return cityStateMember.isValid() && cityStateMember.hasPermission(CityStatePermission.USE_SHOP);
    }

    @Override
    public Inventory createInventory() {
        IndexConfigGUI.Builder guiBuilder = new IndexConfigGUI.Builder();

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_BASIC);
        guiBuilder.fromConfig(yml, bukkitPlayer);
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_BASIC);

        PluginLogger.debug("尝试载入商店 " + shopName + ".");
        if (yml.contains("items")) {
            for (String shopItemName : yml.getConfigurationSection("items").getKeys(false)) {
                ConfigurationSection shopItemSection = yml.getConfigurationSection("items").getConfigurationSection(shopItemName);
                try {
                    RewardType rewardType = validateShopItem(shopItemSection);
                    switch (rewardType) {
                        case NONE -> setNoneReward(shopItemSection, guiBuilder);
                        case COMMANDS -> setCommandReward(shopItemSection, guiBuilder);
                        case CITY_STATE_SET_SPAWN -> setSetCityStateSpawnReward(shopItemSection, guiBuilder);
                        case CITY_STATE_UPGRADE -> setCityStateUpgradeReward(shopItemSection, guiBuilder);
                        case BACK -> setBackReward(shopItemSection, guiBuilder);
                        case CITY_STATE_SHOP -> setShopReward(shopItemSection, guiBuilder);
                        case CITY_STATE_ICON -> setCityStateIconReward(shopItemSection, guiBuilder);
                        case CITY_STATE_TP_ALL -> setCityStateTpAllReward(shopItemSection, guiBuilder);
                    }
                } catch (RuntimeException exception) {
                    disableInvalidItem(shopItemSection, guiBuilder, exception.getMessage());
                }
            }
        }

        PluginLogger.debug("载入商店 " + shopName + " 完毕.");
        return guiBuilder.build();
    }

    private void setNoneReward(@NotNull ConfigurationSection shopItemSection, @NotNull IndexConfigGUI.Builder guiBuilder) {
        guiBuilder.item(shopItemSection.getInt("index") - 1, GUIItemManager.getItemBuilder(shopItemSection.getConfigurationSection("icon"), bukkitPlayer).build());
    }

    /**
     * 命令
     * @param shopItemSection
     * @param guiBuilder
     */
    private void setCommandReward(@NotNull ConfigurationSection shopItemSection, @NotNull IndexConfigGUI.Builder guiBuilder) {
        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, shopItemSection.getCurrentPath());
        ConfigurationSection sellSection = shopItemSection.getConfigurationSection("sell");
        double price = sellSection.getDouble("price");

        guiBuilder.item(GUIItemManager.getIndexItem(shopItemSection, bukkitPlayer, new PlaceholderContainer()
                .add("price", price)), new ItemListener() {
            @Override
            public void onClick(InventoryClickEvent event) {
                if (checkCityStateMoneyOrNotify(price)) {
                    new ShopItemConfirmGUI(CityStateShopGUI.this, cityStateMember, sellSection.getConfigurationSection("ConfirmGUI"), new PlaceholderContainer().add("price", price), price) {
                        @Override
                        public void onPaid() {
                            for (String configured : sellSection.getStringList("commands")) {
                                String command = configured.replace("<player>", bukkitPlayer.getName());
                                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                                    PluginLogger.warning("商店命令执行失败，将退款。路径: " + sellSection.getCurrentPath() + "，命令: " + command);
                                    throw new IllegalStateException("命令执行失败: " + command);
                                }
                            }

                            Util.sendMsg(getBukkitPlayer(), PlaceholderText.replacePlaceholders(sellSection.getString("success_message"), new PlaceholderContainer()
                                    .add("price", price)));
                            back(40L);
                        }
                    }.open();
                }
            }
        });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, shopItemSection.getCurrentPath());
    }

    /**
     * 全员传送
     * @param shopItemSection
     * @param guiBuilder
     */
    private void setCityStateTpAllReward(@NotNull ConfigurationSection shopItemSection, @NotNull IndexConfigGUI.Builder guiBuilder) {
        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, shopItemSection.getCurrentPath());
        ConfigurationSection sellSection = shopItemSection.getConfigurationSection("sell");
        double price = sellSection.getDouble("price");

        guiBuilder.item(GUIItemManager.getIndexItem(shopItemSection, bukkitPlayer, new PlaceholderContainer()
                .add("price", price)), new ItemListener() {
            @Override
            public void onClick(InventoryClickEvent event) {
                if (checkCityStateMoneyOrNotify(price)) {
                    // 必须有1个成员在线（不包括自己）
                    if (cityState.getOnlineMembers().size() <= 1) {
                        Util.sendMsg(bukkitPlayer, tpAllLangSection.getString("no_available_players"));
                        reopen(40L);
                        return;
                    }

                    if (!MainSettings.getCityStateTpAllSendWorlds().contains(bukkitPlayer.getWorld().getName())) {
                        Util.sendMsg(bukkitPlayer, tpAllLangSection.getString("no_send_world"));
                        reopen(40L);
                        return;
                    }

                    new ShopItemConfirmGUI(CityStateShopGUI.this, cityStateMember, sellSection.getConfigurationSection("ConfirmGUI"), new PlaceholderContainer().add("price", price), price) {
                        @Override
                        public boolean canUse() {
                            return super.canUse()
                                    && getCityState().getOnlineMembers().size() > 1
                                    && MainSettings.getCityStateTpAllSendWorlds().contains(cityStateMember.getCityStatePlayer().getBukkitPlayer().getWorld().getName());
                        }

                        @Override
                        public void onPaid() {
                            List<CityStateMember> receiverCityStateMembers = cityState.getOnlineMembers();

                            receiverCityStateMembers.remove(cityStateMember); // 删除自己
                            org.bukkit.Location assemblyLocation = bukkitPlayer.getLocation().clone();
                            List<TpAllRequest> created = new java.util.ArrayList<>();
                            try {
                            receiverCityStateMembers.forEach(receiverCityStateMember -> {
                                Player receiverBukkitPlayer = receiverCityStateMember.getCityStatePlayer().getBukkitPlayer();

                                if (receiverBukkitPlayer == null) {
                                    return;
                                }

                                TpAllRequest request = new TpAllRequest(cityStateMember, receiverCityStateMember, assemblyLocation);
                                request.send();
                                created.add(request);

                                if (MessageService.isTitleEnabled()) {
                                    receiverBukkitPlayer.sendTitle(
                                            LegacyText.getColoredText(tpAllLangSection.getString("received.title")),
                                            LegacyText.getColoredText(tpAllLangSection.getString("received.subtitle")),
                                            0, 20, 20);
                                } else {
                                    Util.sendMsg(receiverBukkitPlayer, tpAllLangSection.getString("received.msg"), new PlaceholderContainer()
                                            .add("sender", cityStateMember.getName())
                                            .add("timeout", LegacyText.secondToStr(MainSettings.getCityStateTpAllTimeout(), LangHelper.Global.getDateTimeUnit()))
                                            .add("shift_count", MainSettings.getCityStateTpAllSneakCount()));
                                }
                            });
                            } catch (RuntimeException exception) {
                                created.stream().filter(request -> plugin.getRequestManager().getRequest(request.getUuid()) == request)
                                        .forEach(Request::delete);
                                throw exception;
                            }

                            Util.sendMsg(getBukkitPlayer(), PlaceholderText.replacePlaceholders(sellSection.getString("success_message"), new PlaceholderContainer()
                                    .add("count", receiverCityStateMembers.size())
                                    .add("price", price)));
                            close();
                        }
                    }.open();
                }
            }
        });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, shopItemSection.getCurrentPath());
    }

    /**
     * 城邦图标
     * @param shopItemSection
     * @param guiBuilder
     */
    private void setCityStateIconReward(@NotNull ConfigurationSection shopItemSection, @NotNull IndexConfigGUI.Builder guiBuilder) {
        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, shopItemSection.getCurrentPath());
        ConfigurationSection sellSection = shopItemSection.getConfigurationSection("sell");
        double price = sellSection.getDouble("price");

        guiBuilder.item(GUIItemManager.getIndexItem(shopItemSection, bukkitPlayer, new PlaceholderContainer()
                .add("price", price)), new ItemListener() {
            @Override
            public void onClick(InventoryClickEvent event) {
                if (checkCityStateMoneyOrNotify(price)) {
                    new ShopItemConfirmGUI(CityStateShopGUI.this, cityStateMember, sellSection.getConfigurationSection("ConfirmGUI"), new PlaceholderContainer().add("price", price), price) {
                        @Override
                        public void onPaid() {
                            ConfigurationSection cityStateIconSection = sellSection.getConfigurationSection("city_state_icon");

                            Material material = Material.matchMaterial(cityStateIconSection.getString("material", ""));
                            if (material == null || material.isAir()) throw new IllegalStateException("城邦图标材料失效");
                            CityStateIcon cityStateIcon = getCityState().giveIcon(material, cityStateIconSection.getString("first_lore"), cityStateIconSection.getString("display_name"));
                            try {
                                getCityState().setCurrentIcon(cityStateIcon);
                            } catch (RuntimeException exception) {
                                getCityState().removeIcon(cityStateIcon);
                                throw exception;
                            }
                            Util.sendMsg(getBukkitPlayer(), PlaceholderText.replacePlaceholders(sellSection.getString("success_message"), new PlaceholderContainer()
                                    .add("display_name", cityStateIcon.getDisplayName())
                                    .add("price", price)));
                            back(40L);
                        }
                    }.open();
                }
            }
        });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, shopItemSection.getCurrentPath());
    }

    /**
     * 设置商店奖励
     * @param shopItemSection
     * @param guiBuilder
     */
    private void setShopReward(@NotNull ConfigurationSection shopItemSection, @NotNull IndexConfigGUI.Builder guiBuilder) {
        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, shopItemSection.getCurrentPath());
        guiBuilder.item(GUIItemManager.getIndexItem(shopItemSection, bukkitPlayer), new ItemListener() {
            @Override
            public void onClick(InventoryClickEvent event) {
                close();
                Shop target = plugin.getShop(shopItemSection.getString("shop"));
                if (target == null) {
                    Util.sendMsg(bukkitPlayer, "&c目标商店不存在。");
                    return;
                }
                new CityStateShopGUI(lastGUI, cityStateMember, target).open();
            }
        });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, shopItemSection.getCurrentPath());
    }

    /**
     * 设置返回奖励
     * @param shopItemSection
     * @param guiBuilder
     */
    private void setBackReward(@NotNull ConfigurationSection shopItemSection, @NotNull IndexConfigGUI.Builder guiBuilder) {
        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, shopItemSection.getCurrentPath());
        guiBuilder.item(GUIItemManager.getIndexItem(shopItemSection, bukkitPlayer), new ItemListener() {
            @Override
            public void onClick(InventoryClickEvent event) {
                back();
            }
        });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, shopItemSection.getCurrentPath());
    }

    /**
     * 设置城邦升级奖励
     * @param shopItemSection
     * @param guiBuilder
     */
    private void setCityStateUpgradeReward(@NotNull ConfigurationSection shopItemSection, @NotNull IndexConfigGUI.Builder guiBuilder) {
        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, shopItemSection.getCurrentPath());
        boolean available = cityState.getMaxMemberCount() + 1 <= MainSettings.getCityStateUpgradeMaxMemberCount();
        ConfigurationSection subItemSection = shopItemSection.getConfigurationSection(available ? "available" : "unavailable");

        if (available) {
            ConfigurationSection sellSection = shopItemSection.getConfigurationSection("sell");
            double price = sellSection.getDouble("price");

            PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, subItemSection.getCurrentPath());
            guiBuilder.item(GUIItemManager.getIndexItem(subItemSection, bukkitPlayer, new PlaceholderContainer()
                    .add("old", cityState.getMaxMemberCount())
                    .add("new", cityState.getMaxMemberCount() + 1)
                    .add("price", price)), new ItemListener() {
                @Override
                public void onClick(InventoryClickEvent event) {
                    if (checkCityStateMoneyOrNotify(price)) {
                        new ShopItemConfirmGUI(CityStateShopGUI.this, cityStateMember, sellSection.getConfigurationSection("ConfirmGUI"), new PlaceholderContainer().add("price", price), price) {
                            @Override
                            public boolean canUse() {
                                return super.canUse() && getCityState().getMaxMemberCount() + 1 <= MainSettings.getCityStateUpgradeMaxMemberCount();
                            }

                            @Override
                            public void onPaid() {
                                getCityState().setAdditionMemberCount(getCityState().getAdditionMemberCount() + 1);
                                Util.sendMsg(getBukkitPlayer(), PlaceholderText.replacePlaceholders(sellSection.getString("success_message"), new PlaceholderContainer()
                                        .add("old", cityState.getMaxMemberCount() - 1)
                                        .add("new", cityState.getMaxMemberCount())
                                        .add("price", price)));
                                back(40L);
                            }
                        }.open();
                    }
                }
            });
            PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, subItemSection.getCurrentPath());
        } else {
            PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, subItemSection.getCurrentPath());
            guiBuilder.item(GUIItemManager.getIndexItem(subItemSection, bukkitPlayer));
            PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, subItemSection.getCurrentPath());
        }
        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, shopItemSection.getCurrentPath());
    }

    /**
     * 设置设置城邦主城奖励
     * @param shopItemSection
     * @param guiBuilder
     */
    private void setSetCityStateSpawnReward(@NotNull ConfigurationSection shopItemSection, @NotNull IndexConfigGUI.Builder guiBuilder) {
        throw new IllegalStateException("独立城邦世界使用模板固定出生点，设置主城商品已停用");
        /* Legacy implementation intentionally remains unreachable so old configurations are parsed
           and rendered as a disabled barrier instead of charging players. */
        /*
        ConfigurationSection sellSection = shopItemSection.getConfigurationSection("sell");
        double price = sellSection.getDouble("price");

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, shopItemSection.getCurrentPath());
        guiBuilder.item(GUIItemManager.getIndexItem(shopItemSection, bukkitPlayer, new PlaceholderContainer()
                .add("price", price)), new ItemListener() {
            @Override
            public void onClick(InventoryClickEvent event) {
                if (checkCityStateMoneyOrNotify(price)) {
                    new ShopItemConfirmGUI(CityStateShopGUI.this, cityStateMember, sellSection.getConfigurationSection("ConfirmGUI"), new PlaceholderContainer().add("price", price), price) {
                        @Override
                        public void onPaid() {
                            getCityState().setSpawn(getBukkitPlayer().getLocation());
                            Util.sendMsg(getBukkitPlayer(), PlaceholderText.replacePlaceholders(sellSection.getString("success_message"), new PlaceholderContainer().add("price", com.cuzz.rookiecitystate.internal.text.TextService.formatDecimal(price))));
                            back(40L);
                        }
                    }.open();
                }
            }
        });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, shopItemSection.getCurrentPath());
        */
    }

    /**
     * 检查金币是否足够，不够则提示并返回false，否则返回true
     * @param price
     * @return
     */
    private boolean checkCityStateMoneyOrNotify(double price) {
        if (!cityState.getCityStateBank().has(CityStateBank.BalanceType.GMONEY, price)) {
            Util.sendMsg(bukkitPlayer, thisLangSection.getString("gmoney_not_enough"), new PlaceholderContainer()
                    .add("need", BigDecimal.valueOf(price).subtract(cityState.getCityStateBank().getBalance(CityStateBank.BalanceType.GMONEY))));
            reopen(40L);
            return false;
        }

        return true;
    }

    private RewardType validateShopItem(ConfigurationSection item) {
        String rawType = item.getString("reward_type");
        if (rawType == null) throw new IllegalArgumentException("缺少 reward_type");
        RewardType type;
        try {
            type = RewardType.valueOf(rawType);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("未知 reward_type: " + rawType);
        }
        if (type == RewardType.CITY_STATE_UPGRADE) {
            if (item.getConfigurationSection("available") == null || item.getConfigurationSection("unavailable") == null) {
                throw new IllegalArgumentException("缺少 available/unavailable 配置");
            }
        } else {
            int index = item.getInt("index", 0);
            if (index <= 0 || index > yml.getInt("row", 1) * 9) throw new IllegalArgumentException("槽位越界");
        }
        if (type == RewardType.CITY_STATE_SHOP) {
            String target = item.getString("shop");
            if (target == null || plugin.getShop(target) == null) throw new IllegalArgumentException("目标商店不存在: " + target);
        }
        if (type == RewardType.NONE || type == RewardType.BACK || type == RewardType.CITY_STATE_SHOP) return type;
        ConfigurationSection sell = item.getConfigurationSection("sell");
        if (sell == null) throw new IllegalArgumentException("缺少 sell 配置");
        double price = sell.getDouble("price", Double.NaN);
        if (!Double.isFinite(price) || price < 0D) throw new IllegalArgumentException("price 必须是非负数");
        if (sell.getConfigurationSection("ConfirmGUI") == null) throw new IllegalArgumentException("缺少 ConfirmGUI");
        if (type == RewardType.COMMANDS && (sell.getStringList("commands").isEmpty()
                || sell.getStringList("commands").stream().anyMatch(String::isBlank))) {
            throw new IllegalArgumentException("commands 不能为空");
        }
        if (type == RewardType.CITY_STATE_ICON) {
            ConfigurationSection icon = sell.getConfigurationSection("city_state_icon");
            Material material = icon == null ? null : Material.matchMaterial(icon.getString("material", ""));
            if (material == null || material.isAir()) throw new IllegalArgumentException("city_state_icon.material 无效");
        }
        return type;
    }

    private void disableInvalidItem(ConfigurationSection item, IndexConfigGUI.Builder builder, String reason) {
        PluginLogger.warning("已禁用错误的商店商品 " + item.getCurrentPath() + ": " + reason);
        int index = item.getInt("index", 0) - 1;
        int size = yml.getInt("row", 1) * 9;
        if (index >= 0 && index < size) {
            builder.item(index, new com.cuzz.rookiecitystate.internal.item.ItemBuilder()
                    .material(Material.BARRIER).colored().displayName("&c商品配置错误")
                    .lores(List.of("&7" + reason)).build());
        }
    }
}
