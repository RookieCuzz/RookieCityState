package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.DebugMessage;
import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.gui.BasePageableGUI;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.citystate.member.CityStatePermission;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.internal.inventory.InventoryListener;
import com.cuzz.rookiecitystate.internal.inventory.ItemListener;
import com.cuzz.rookiecitystate.internal.item.ItemBuilder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CityStateMemberListGUI extends BasePageableGUI {
    private enum ViewerType {PLAYER, MANAGER}
    private static final List<CityStatePermission> MANAGER_CITY_STATE_PERMISSIONS = Arrays.asList(CityStatePermission.MEMBER_KICK, CityStatePermission.MANAGE_PERMISSION);
    private final RookieCityState plugin = RookieCityState.inst();
    private final ViewerType viewerType;
    private final CityState cityState;
    private final Player bukkitPlayer = getBukkitPlayer();
    private ConfigurationSection thisGUISection;
    private List<Integer> itemIndexes;
    private int itemIndexCount;
    private List<CityStateMember> members;
    private int memberCount;

    public CityStateMemberListGUI(@Nullable GUI lastGUI, @NotNull CityState cityState, @NotNull CityStateMember cityStateMember) {
        this(lastGUI, cityState, cityStateMember.getCityStatePlayer());
    }

    public CityStateMemberListGUI(@Nullable GUI lastGUI, @NotNull CityState cityState, @NotNull CityStatePlayer cityStatePlayer) {
        super(lastGUI, Type.MEMBER_LIST, cityStatePlayer);

        this.cityState = cityState;

        CityStateMember member = cityState.getMember(cityStatePlayer);

        out:
        if (member == null) {
            viewerType = ViewerType.PLAYER;
        } else {
            for (CityStatePermission cityStatePermission : MANAGER_CITY_STATE_PERMISSIONS) {
                if (member.hasPermission(cityStatePermission)) {
                    viewerType = ViewerType.MANAGER;
                    break out;
                }
            }

            viewerType = ViewerType.PLAYER;
        }

        this.thisGUISection = plugin.getGUIYaml("CityStateMemberListGUI").getConfigurationSection(viewerType.name().toLowerCase());

        PluginLogger.debug("开始: 加载 'items.member.indexes'.");
        this.itemIndexes = Util.getIndexes(thisGUISection.getString( "items.member.indexes")); // 得到所有可供城邦设置的位置
        PluginLogger.debug("结束: 加载 'items.member.indexes'.");

        this.itemIndexCount = itemIndexes.size();
    }

    @Override
    public void update() {
        this.members = cityState.getMembers();
        this.members.sort(Comparator.comparingLong(CityStateMember::getJoinTime));
        this.memberCount = members.size();

        setPageCount(memberCount % itemIndexCount == 0 ? memberCount / itemIndexCount : memberCount / itemIndexCount + 1);
    }

    @Override
    public Inventory createInventory() {
        Map<Integer, CityStateMember> indexMap = new HashMap<>();
        IndexConfigGUI.Builder guiBuilder = new IndexConfigGUI.Builder();

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_BASIC);
        guiBuilder.fromConfig(thisGUISection, bukkitPlayer, new PlaceholderContainer()
                        .add("page", getCurrentPage() + 1)
                        .add("total_page", getPageCount()));
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_BASIC);

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.page_items");
        guiBuilder.pageItems(thisGUISection.getConfigurationSection("items.page_items"), this);
        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.page_items");

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.back");
        guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.back"), bukkitPlayer, new PlaceholderContainer().addCityStatePlaceholders(cityState)), new ItemListener() {
            @Override
            public void onClick(InventoryClickEvent event) {
                if (canBack()) {
                    back();
                }
            }
        });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.back");


        if (viewerType == ViewerType.MANAGER) {
            guiBuilder.listener(new InventoryListener() {
                        @Override
                        public void onClick(InventoryClickEvent event) {
                            int slot = event.getRawSlot();

                            if (indexMap.containsKey(slot)) {
                                CityStateMember cityStateMember = indexMap.get(slot);

                                if (!cityStateMember.isValid()) {
                                    reopen();
                                    return;
                                }

                                close();

                                new CityStateMemberManageGUI(CityStateMemberListGUI.this, CityStateMemberListGUI.this.cityState.getMember(cityStatePlayer), cityStateMember).open();
                            }
                        }
                    });
        }

        if (getPageCount() > 0) {
            int memberCounter = getCurrentPage() * itemIndexes.size();
            int loopCount = memberCount - memberCounter < itemIndexCount ? memberCount - memberCounter : itemIndexCount;

            for (int i = 0; i < loopCount; i++) {
                CityStateMember cityStateMember = members.get(memberCounter++);
                PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.member.icon");
                ItemBuilder itemBuilder = GUIItemManager.getItemBuilder(thisGUISection.getConfigurationSection("items.member.icon"), bukkitPlayer, new PlaceholderContainer()
                        .addCityStatePlaceholders(cityState)
                        .addCityStateMemberPlaceholders(cityStateMember));
                PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.member.icon");

                // 管理模式
                if (viewerType == ViewerType.MANAGER) {
                    indexMap.put(itemIndexes.get(i), cityStateMember);
                }

                guiBuilder.item(itemIndexes.get(i), itemBuilder.build());
            }
        }

        return guiBuilder.build();
    }

    @Override
    public boolean canUse() {
        return cityState.isValid();
    }
}
