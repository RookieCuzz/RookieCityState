package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.DebugMessage;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.gui.BasePageableGUI;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.CityStateIcon;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.internal.chat.ChatInterceptor;
import com.cuzz.rookiecitystate.internal.chat.ChatListener;
import com.cuzz.rookiecitystate.internal.inventory.InventoryListener;
import com.cuzz.rookiecitystate.internal.inventory.ItemListener;
import com.cuzz.rookiecitystate.internal.item.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
/*
返回时强制更新，手动强制更新
 */

/**
 * 主GUI
 * @version 1.0.0
 */
public class MainGUI extends BasePageableGUI {
    private final RookieCityState plugin = RookieCityState.inst();
    private final Player bukkitPlayer = cityStatePlayer.getBukkitPlayer();
    private final String playerName = bukkitPlayer.getName();
    private final ConfigurationSection thisGUISection = plugin.getGUIYaml("MainGUI");
    private final ConfigurationSection thisLangSection = plugin.getLangYaml().getConfigurationSection("MainGUI");
    private final List<Integer> itemIndexes; // 得到所有可供城邦设置的位置
    private final int itemIndexCount;
    private List<CityState> cityStates;
    private int cityStateCount;

    public MainGUI(@NotNull CityStatePlayer cityStatePlayer) {
        super(null, Type.MAIN, cityStatePlayer);

        PluginLogger.debug("加载 'items.city_state.indexes'.");
        this.itemIndexes = Util.getIndexes(thisGUISection.getString("items.city_state.indexes"));
        this.itemIndexCount = itemIndexes.size();
    }

    @Override
    public void update() {
        this.cityStates = plugin.getCacheCityStateManager().getSortedCityStates();
        this.cityStateCount = cityStates.size();

        setPageCount(cityStateCount % itemIndexCount == 0 ? cityStateCount / itemIndexCount : cityStateCount / itemIndexCount + 1);
    }

    @Override
    public Inventory createInventory() {
        Map<Integer, CityState> indexMap = new HashMap<>(); // slot 对应的城邦uuid
        IndexConfigGUI.Builder guiBuilder = new IndexConfigGUI.Builder();

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_BASIC);
        guiBuilder.fromConfig(thisGUISection, bukkitPlayer, new PlaceholderContainer()
                        .add("page", String.valueOf(getCurrentPage() + 1))
                        .add("total_page", String.valueOf(getPageCount())));
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_BASIC);

        guiBuilder
                .colored()
                .listener(new InventoryListener() {
                    @Override
                    public void onClick(InventoryClickEvent event) {
                        int slot = event.getRawSlot();

                        if (indexMap.containsKey(slot)) {
                            close();

                            CityState cityState = indexMap.get(slot);

                            if (!cityState.isValid()) {
                                reopen();
                                return;
                            }

                            if (cityState.isMember(cityStatePlayer)) {
                                new CityStateMineGUI(MainGUI.this, cityState.getMember(cityStatePlayer)).open();
                            } else {
                                new CityStateInfoGUI(MainGUI.this, cityStatePlayer, cityState).open();
                            }
                        }
                    }
                });


        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.page_items");
        guiBuilder.pageItems(thisGUISection.getConfigurationSection("items.page_items"), this);

        if (thisGUISection.contains("items.popular_city_states")) {
            guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.popular_city_states"),
                    bukkitPlayer), event -> {
                close();
                new PopularCityStateGUI(MainGUI.this, cityStatePlayer).open();
            });
        }


        if (cityStatePlayer.isInCityState()) {
			PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.my_city_state");
            guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.my_city_state"), bukkitPlayer, new PlaceholderContainer()
                    .add("%PLAYER%", playerName)), new ItemListener() {
                @Override
                public void onClick(InventoryClickEvent event) {
                    close();
                    new CityStateMineGUI(MainGUI.this, cityStatePlayer.getCityState().getMember(cityStatePlayer)).open();
                }
            });
            PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.my_city_state");
        } else {
            PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.create_city_state");
            guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.create_city_state"), bukkitPlayer), new ItemListener() {
                @Override
                public void onClick(InventoryClickEvent event) {
                    close();
                    Util.sendMsg(bukkitPlayer, thisLangSection.getString("create.input.tip"), new PlaceholderContainer()
                            .add("cancel_str", MainSettings.getCityStateCreateInputCancelStr()));
                    new ChatInterceptor.Builder()
                            .plugin(plugin)
                            .player(bukkitPlayer)
                            .onlyFirst(true)
                            .timeout(MainSettings.getCityStateCreateInputWaitSec())
                            .chatListener(new ChatListener() {
                                @Override
                                public void onChat(String message) {
                                    String msg = message;

                                    if (msg.equalsIgnoreCase(MainSettings.getCityStateCreateInputCancelStr())) {
                                        Util.sendMsg(bukkitPlayer, thisLangSection.getString("create.input.cancelled"));
                                        return;
                                    }

                                    String cityStateName = ChatColor.translateAlternateColorCodes('&', msg);

                                    if (MainSettings.isCityStateCreateNoDuplicationName()) {
                                        for (CityState cityState : plugin.getCityStateManager().getCityStates()) {
                                            if (cityState.getName().equalsIgnoreCase(cityStateName)) {
                                                Util.sendMsg(bukkitPlayer, thisLangSection.getString("create.input.no_duplication_name"));
                                                return;
                                            }
                                        }
                                    }

                                    if (!cityStateName.matches(MainSettings.getCityStateCreateNameRegex())) {
                                        Util.sendMsg(bukkitPlayer, thisLangSection.getString("create.input.regex_not_match"));
                                        return;
                                    }

                                    if (cityStateName.contains("§")
                                            && !bukkitPlayer.hasPermission("rookiecitystate.create.colored")) {
                                        Util.sendMsg(bukkitPlayer, thisLangSection.getString("create.input.no_colored_name_permission"));
                                        return;
                                    }

                                    new BukkitRunnable() {
                                        @Override
                                        public void run() {
                                            new CityStateCreateGUI(MainGUI.this, cityStatePlayer, cityStateName).open();
                                        }
                                    }.runTask(plugin);
                                }

                                @Override
                                public void onTimeout() {
                                    Util.sendMsg(bukkitPlayer, thisLangSection.getString("create.input.timeout"));
                                }
                            }).build().register();
                }
            });
            PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.create_city_state");
        }

        int cityStateCounter = getCurrentPage() * itemIndexCount;
        int loopCount = cityStateCount - cityStateCounter < itemIndexCount ? cityStateCount - cityStateCounter : itemIndexCount; // 循环次数，根据当前能够显示的数量决定

        if (getPageCount() > 0) {
            // 城邦图标
            for (int i = 0; i < loopCount; i++) {
                CityState cityState = cityStates.get(cityStateCounter++);
                PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.city_state.icon");
                ItemBuilder itemBuilder = GUIItemManager.getItemBuilder(thisGUISection.getConfigurationSection("items.city_state.icon"), bukkitPlayer, new PlaceholderContainer().addCityStatePlaceholders(cityState));
                PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.city_state.icon");
                CityStateIcon cityStateIcon = cityState.getCurrentIcon();

                if (cityStateIcon != null) {
                    itemBuilder.material(cityStateIcon.getMaterial());

                    String firstLore = cityStateIcon.getFirstLore();

                    if (firstLore != null && !firstLore.equals("")) {
                        itemBuilder.insertLore(0, cityStateIcon.getFirstLore());
                    }
                } else {
                    itemBuilder.material(MainSettings.getCityStateIconDefaultMaterial());

                    String firstLore = MainSettings.getCityStateIconDefaultFirstLore();

                    if (firstLore != null && !firstLore.equals("")) {
                        itemBuilder.insertLore(0, MainSettings.getCityStateIconDefaultFirstLore());
                    }
                }

                indexMap.put(itemIndexes.get(i), cityState);
                guiBuilder.item(itemIndexes.get(i), itemBuilder.build());
            }
        }
        guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.back")), new ItemListener() {
            @Override
            public void onClick(InventoryClickEvent event) {
                close();

                String cmd = thisGUISection.getString("items.back.command");

                if (cmd == null) {
                    throw new RuntimeException("items.back.command 不能为空.");
                }

                String sender = thisGUISection.getString("items.back.sender");

                if (sender == null) {
                    throw new RuntimeException("items.back.sender 不能为空.");
                } else if (sender.equalsIgnoreCase("PLAYER")) {
                    bukkitPlayer.performCommand(cmd.replace("<player>", bukkitPlayer.getName()));
                } else if (sender.equalsIgnoreCase("CONSOLE")) {
                    String command = cmd.replace("<player>", bukkitPlayer.getName());
                    if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                        PluginLogger.warning("GUI 命令执行失败: " + command);
                    }
                } else {
                    throw new RuntimeException("items.back.sender 不合法");
                }
            }
        });

        return guiBuilder.build();
    }

    @Override
    public boolean canUse() {
        return true;
    }
}
