package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.DebugMessage;
import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.config.gui.PriorityConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.config.gui.item.PriorityItem;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.gui.BasePlayerGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.CityStateBank;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.citystate.member.CityStateMemberSign;
import com.cuzz.rookiecitystate.citystate.member.CityStatePermission;
import com.cuzz.rookiecitystate.citystate.member.CityStatePosition;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.placeholder.PlaceholderText;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.internal.chat.ChatInterceptor;
import com.cuzz.rookiecitystate.internal.chat.ChatListener;
import com.cuzz.rookiecitystate.internal.inventory.ItemListener;
import com.cuzz.rookiecitystate.internal.text.MessageService;
import com.cuzz.rookiecitystate.internal.text.LegacyText;
import com.cuzz.rookiecitystate.internal.text.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CityStateMineGUI extends BasePlayerGUI {
    private final RookieCityState plugin = RookieCityState.inst();
    private final ConfigurationSection thisGUISection = plugin.getGUIYaml("CityStateMineGUI");
    private final ConfigurationSection thisLangSection = plugin.getLangYaml().getConfigurationSection("CityStateMineGUI");
    private final Player bukkitPlayer;
    private final CityStatePosition cityStatePosition;
    private final CityStateMember cityStateMember;
    private final CityStateMemberSign cityStateMemberSign;
    private final CityState cityState;

    public CityStateMineGUI(@Nullable GUI lastGUI, @NotNull CityStateMember cityStateMember) {
        super(lastGUI, Type.MINE, cityStateMember.getCityStatePlayer());

        this.cityStateMember = cityStateMember;
        this.cityStateMemberSign = cityStateMember.getSign();
        this.bukkitPlayer = cityStatePlayer.getBukkitPlayer();
        this.cityStatePosition = cityStateMember.getPosition();
        this.cityState = cityStateMember.getCityState();
    }

    @Override
    public Inventory createInventory() {
        PriorityConfigGUI.Builder guiBuilder = new PriorityConfigGUI.Builder();

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_BASIC);
        guiBuilder.fromConfig(thisGUISection, bukkitPlayer);
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_BASIC);

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

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.city_state_info");
        guiBuilder.item(GUIItemManager.getPriorityItem(thisGUISection.getConfigurationSection("items.city_state_info"), bukkitPlayer, new PlaceholderContainer().addCityStatePlaceholders(cityState)));
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.city_state_info");

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.self_info");
        guiBuilder.item(GUIItemManager.getPriorityItem(thisGUISection.getConfigurationSection("items.self_info"), bukkitPlayer, new PlaceholderContainer().addCityStateMemberPlaceholders(cityStateMember)));
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.self_info");

        {
            String path = "items.city_state_members." + ((cityStateMember.hasPermission(CityStatePermission.MEMBER_KICK) || cityStateMember.hasPermission(CityStatePermission.MANAGE_PERMISSION)) ? "manager" : "member");

            PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, path);
            guiBuilder.item(GUIItemManager.getPriorityItem(thisGUISection.getConfigurationSection(path), bukkitPlayer), new ItemListener() {
                @Override
                public void onClick(InventoryClickEvent event) {
                    close();
                    new CityStateMemberListGUI(CityStateMineGUI.this, cityState, cityStateMember).open();
                }
            });
            PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, path);
        }

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.city_state_donate");
        guiBuilder.item(GUIItemManager.getPriorityItem(thisGUISection.getConfigurationSection("items.city_state_donate"), bukkitPlayer), new ItemListener() {
                    @Override
                    public void onClick(InventoryClickEvent event) {
                        close();
                        new CityStateDonateGUI(CityStateMineGUI.this, cityStateMember).open();
                    }
                });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.city_state_donate");

        // 城邦主城
        {
            String path = "items.city_state_spawn." + (cityState.hasSpawn() ? "available" : "unavailable");

            PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, path);
            guiBuilder.item(GUIItemManager.getPriorityItem(thisGUISection.getConfigurationSection(path), bukkitPlayer), cityState.hasSpawn() ? new ItemListener() {
                        @Override
                        public void onClick(InventoryClickEvent event) {
                            executeCityStateSpawn();
                        }
                    } : null);
            PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, path);
        }

        List<String> originalAnnouncements = cityState.getAnnouncements().stream().map(s -> "§f" + s).collect(Collectors.toList());

        // 设置城邦公告
        if (cityStateMember.hasPermission(CityStatePermission.SET_ANNOUNCEMENTS)) {
            PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.city_state_announcement.setter");
            PriorityItem priorityItem = GUIItemManager.getPriorityItem(thisGUISection.getConfigurationSection("items.city_state_announcement.setter"), bukkitPlayer);

            List<String> lores = new ArrayList<>(originalAnnouncements);

            if (lores.size() == 0) {
                lores.addAll(MainSettings.getCityStateAnnouncementDefault());
            }

            Optional.ofNullable(thisGUISection.getStringList("items.city_state_announcement.setter.icon.append_lores")).orElse(new ArrayList<>()).forEach(s -> lores.add(PlaceholderText.replacePlaceholders(s, new PlaceholderContainer()
                    .add("split_str", MainSettings.getCityStateAnnouncementSplitStr())
                    .add("max", MainSettings.getCityStateAnnouncementMaxCount()))));

            priorityItem.getItemBuilder().lores(lores);
            guiBuilder.item(priorityItem, new ItemListener() {
                @Override
                public void onClick(InventoryClickEvent event) {
                    executeSetCityStateAnnouncement();
                }
            });
            PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.city_state_announcement.setter");
            // 获取城邦公告
        } else {
            PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.city_state_announcement.getter");
            PriorityItem priorityItem = GUIItemManager.getPriorityItem(thisGUISection.getConfigurationSection("items.city_state_announcement.getter"), bukkitPlayer);
            List<String> lores = new ArrayList<>(originalAnnouncements);

            if (lores.size() == 0) {
                lores.addAll(MainSettings.getCityStateAnnouncementDefault());
            }

            priorityItem.getItemBuilder().lores(lores);
            guiBuilder.item(priorityItem);
            PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.city_state_announcement.getter");
        }

        // 城邦签到
        {
            boolean isSignedToday = cityStateMemberSign.isSignedToday();
            String path = "items.city_state_sign." + (cityStateMemberSign.isSignedToday() ? "unavailable" : "available");

            PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, path);
            guiBuilder.item(GUIItemManager.getPriorityItem(thisGUISection.getConfigurationSection(path), bukkitPlayer, new PlaceholderContainer()
                    .add("signed_count", cityStateMember.getSign().getSignedCount())), !isSignedToday ? new ItemListener() {
                @Override
                public void onClick(InventoryClickEvent event) {
                    executeCityStateSign();
                }
            } : null);
            PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, path);
        }

        // 城邦商店
        if (cityStateMember.hasPermission(CityStatePermission.USE_SHOP)) {
            PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.city_state_shop");
            guiBuilder.item(GUIItemManager.getPriorityItem(thisGUISection.getConfigurationSection("items.city_state_shop"), bukkitPlayer), new ItemListener() {
                @Override
                public void onClick(InventoryClickEvent event) {
                    new CityStateShopGUI(CityStateMineGUI.this, cityStateMember).open();
                }
            });
            PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.city_state_shop");
        }

        // 成员免伤
        if (cityStateMember.hasPermission(CityStatePermission.SET_MEMBER_DAMAGE)) {
            String path = "items.city_state_set_member_damage." + (cityState.isMemberDamageEnabled() ? "turn_off" : "turn_on");

            PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, path);
            guiBuilder.item(GUIItemManager.getPriorityItem(thisGUISection.getConfigurationSection(path), bukkitPlayer), new ItemListener() {
                @Override
                public void onClick(InventoryClickEvent event) {
                    cityState.setMemberDamageEnabled(!cityState.isMemberDamageEnabled());
                    Util.sendMsg(bukkitPlayer, thisLangSection.getString("city_state_set_member_damage." + (!cityState.isMemberDamageEnabled() ? "turn_off" : "turn_on")));
                    reopen();
                }
            });
            PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, path);
        }

        // 入会审批
        if (cityStateMember.hasPermission(CityStatePermission.PLAYER_JOIN_CHECK)) {
            PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.city_state_join_check");
            guiBuilder.item(GUIItemManager.getPriorityItem(thisGUISection.getConfigurationSection("items.city_state_join_check"), bukkitPlayer), new ItemListener() {
                        @Override
                        public void onClick(InventoryClickEvent event) {
                            close();
                            new CityStateJoinCheckGUI(CityStateMineGUI.this, cityStateMember).open();
                        }
                    });
            PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.city_state_join_check");
        }

        // 图标仓库
        if (cityStateMember.hasPermission(CityStatePermission.USE_ICON_REPOSITORY)) {
            PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.city_state_icon_repository");
            guiBuilder.item(GUIItemManager.getPriorityItem(thisGUISection.getConfigurationSection("items.city_state_icon_repository"), bukkitPlayer), new ItemListener() {
                @Override
                public void onClick(InventoryClickEvent event) {
                    close();
                    new CityStateIconRepositoryGUI(CityStateMineGUI.this, cityStateMember).open();
                }
            });
            PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.city_state_icon_repository");
        }

        // 解散
        if (cityStatePosition == CityStatePosition.OWNER) {
            PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.city_state_delete");
            guiBuilder.item(GUIItemManager.getPriorityItem(thisGUISection.getConfigurationSection("items.city_state_delete"), bukkitPlayer), new ItemListener() {
                @Override
                public void onClick(InventoryClickEvent event) {
                    executeCityStateDelete();
                }
            });
            PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.city_state_delete");
        // 退出
        } else {
            PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.city_state_leave");
            guiBuilder.item(GUIItemManager.getPriorityItem(thisGUISection.getConfigurationSection("items.city_state_leave"), bukkitPlayer), new ItemListener() {
                @Override
                public void onClick(InventoryClickEvent event) {
                    executeCityStateLeave();
                }
            });
            PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.city_state_leave");
        }

        return guiBuilder.build();
    }

    private void executeSetCityStateAnnouncement() {
        close();
        Util.sendMsg(bukkitPlayer, thisLangSection.getString("city_state_announcement.set.input"), new PlaceholderContainer()
                .add("split_str", MainSettings.getCityStateAnnouncementSplitStr())
                .add("max", MainSettings.getCityStateAnnouncementMaxCount()));

        new ChatInterceptor.Builder().chatListener(new ChatListener() {
            @Override
            public void onChat(String message) {
                String msg = message;

                if (msg.equalsIgnoreCase(MainSettings.getCityStateAnnouncementInputCancelStr())) {
                    Util.sendMsg(bukkitPlayer, thisLangSection.getString("city_state_announcement.set.cancelled"));
                    return;
                }

                String[] announcements = msg.split(MainSettings.getCityStateAnnouncementSplitStr());

                if (announcements.length > MainSettings.getCityStateAnnouncementMaxCount()) {
                    Util.sendMsg(bukkitPlayer, thisLangSection.getString("city_state_announcement.set.max"), new PlaceholderContainer()
                            .add("len", announcements.length)
                            .add("max", MainSettings.getCityStateAnnouncementMaxCount()));
                    return;
                }

                for (String announcement : announcements) {
                    Util.sendMsg(bukkitPlayer, thisLangSection.getString("city_state_announcement.set.list"), new PlaceholderContainer()
                            .add("announcement", announcement));
                }

                Util.sendMsg(bukkitPlayer, thisLangSection.getString("city_state_announcement.set.success"));
                cityState.setAnnouncements(LegacyText.getColoredTexts(Arrays.asList(announcements)));
            }
        }).player(bukkitPlayer).plugin(plugin).onlyFirst(true).build().register();
    }

    private void executeCityStateSign() {
        if (cityStateMemberSign.isSignedToday()) {
            reopen();
            return;
        }

        cityStateMemberSign.signToday();

        double gmoney = MainSettings.getCityStateSignRewardGMoney();

        if (gmoney > 0) {
            cityState.getCityStateBank().deposit(CityStateBank.BalanceType.GMONEY, gmoney);
            cityStateMember.addDonated(CityStateBank.BalanceType.GMONEY, gmoney);
        }

        Optional.ofNullable(MainSettings.getCityStateSignRewardCommands()).orElse(new ArrayList<>()).forEach(s -> {
            String command = s.replace("<player>", bukkitPlayer.getName());
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                PluginLogger.warning("签到奖励命令执行失败: " + command);
            }
        });

        Util.sendMsg(bukkitPlayer, thisLangSection.getString("city_state_sign.success"));
        reopen(40L);
    }

    private void executeCityStateDelete() {
        close();
        Util.sendMsg(bukkitPlayer, thisLangSection.getString("city_state_delete.confirm"), new PlaceholderContainer()
                .add("wait", MainSettings.getCityStateDismissWait())
                .add("confirm_str", MainSettings.getCityStateDismissConfirmStr()));
        new ChatInterceptor.Builder()
                .player(bukkitPlayer)
                .plugin(plugin)
                .onlyFirst(true)
                .timeout(MainSettings.getCityStateDismissWait())
                .chatListener(new ChatListener() {
                    @Override
                    public void onChat(String message) {
                        if (ChatColor.stripColor(message).equalsIgnoreCase(MainSettings.getCityStateDismissConfirmStr())) {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (cityState.isValid()) {
                                        cityState.delete();
                                    }
                                }
                            }.runTask(plugin);

                            Util.sendMsg(bukkitPlayer, thisLangSection.getString("city_state_delete.success"));
                        } else {
                            Util.sendMsg(bukkitPlayer, thisLangSection.getString("city_state_delete.failed"));
                        }
                    }

                    @Override
                    public void onTimeout() {
                        Util.sendMsg(bukkitPlayer, thisLangSection.getString("city_state_delete.timeout"));
                    }
                }).build().register();
    }

    private void executeCityStateLeave() {
        close();
        Util.sendMsg(bukkitPlayer, thisLangSection.getString("city_state_leave.confirm"), new PlaceholderContainer()
                .add("wait", MainSettings.getCityStateDismissWait())
                .add("confirm_str", MainSettings.getCityStateDismissConfirmStr()));
        new ChatInterceptor.Builder()
                .player(bukkitPlayer)
                .plugin(plugin)
                .onlyFirst(true)
                .timeout(MainSettings.getCityStateExitWait())
                .chatListener(new ChatListener() {
                    @Override
                    public void onChat(String message) {
                        if (ChatColor.stripColor(message).equalsIgnoreCase(MainSettings.getCityStateExitConfirmStr())) {
                            cityState.removeMember(cityStateMember);
                            Util.sendMsg(bukkitPlayer, thisLangSection.getString("city_state_leave.success"));
                        } else {
                            Util.sendMsg(bukkitPlayer, thisLangSection.getString("city_state_leave.failed"));
                        }
                    }

                    @Override
                    public void onTimeout() {
                        Util.sendMsg(bukkitPlayer, thisLangSection.getString("city_state_leave.timeout"));
                    }
                }).build().register();
    }

    private void executeCityStateSpawn() {
        if (!cityState.hasSpawn() || cityState.getSpawn().getLocation() == null) {
            Util.sendMsg(bukkitPlayer, "&c城邦主城所在世界不可用。");
            reopen();
            return;
        }

        close();
        plugin.getTeleportService().begin(
                bukkitPlayer,
                cityState.getSpawn().getLocation(),
                MainSettings.getCityStateSpawnTeleportWait(),
                remaining -> bukkitPlayer.sendTitle(
                        LegacyText.getColoredText(PlaceholderText.replacePlaceholders(thisLangSection.getString("city_state_spawn.count_down.title"),
                                new PlaceholderContainer().add("count_down", remaining))),
                        LegacyText.getColoredText(PlaceholderText.replacePlaceholders(thisLangSection.getString("city_state_spawn.count_down.subtitle"),
                                new PlaceholderContainer().add("count_down", remaining))), 0, 20, 20),
                () -> bukkitPlayer.sendTitle(
                        LegacyText.getColoredText(thisLangSection.getString("city_state_spawn.teleported.title")),
                        LegacyText.getColoredText(thisLangSection.getString("city_state_spawn.teleported.subtitle")), 0, 20, 20),
                () -> bukkitPlayer.sendTitle(
                        LegacyText.getColoredText(thisLangSection.getString("city_state_spawn.cancelled.title")),
                        LegacyText.getColoredText(thisLangSection.getString("city_state_spawn.cancelled.subtitle")), 0, 20, 20),
                failure -> Util.sendMsg(bukkitPlayer, "&c传送失败: " + failure.getMessage()));
    }

    @Override
    public boolean canUse() {
        return cityStateMember.isValid();
    }
}
