package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.DebugMessage;
import com.cuzz.rookiecitystate.config.gui.PriorityConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.PriorityItem;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.member.CityStatePermission;
import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.gui.BaseConfirmGUI;
import com.cuzz.rookiecitystate.gui.BasePlayerGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.internal.inventory.ItemListener;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class CityStateMemberManageGUI extends BasePlayerGUI {
    private final RookieCityState plugin = RookieCityState.inst();
    private final ConfigurationSection thisGUISection = plugin.getGUIYaml("CityStateMemberManageGUI");
    private final CityState cityState;
    private final CityStateMember managerCityStateMember;
    private final CityStateMember targetCityStateMember;
    private final Player targetBukkitPlayer;
    private Set<CityStatePermission> ownedPermissions;


    public CityStateMemberManageGUI(@Nullable GUI lastGUI, @NotNull CityStateMember mangerCityStateMember, @NotNull CityStateMember targetCityStateMember) {
        super(lastGUI, Type.MEMBER_MANAGE, mangerCityStateMember.getCityStatePlayer());

        this.managerCityStateMember = mangerCityStateMember;
        this.targetCityStateMember = targetCityStateMember;
        this.targetBukkitPlayer = targetCityStateMember.getCityStatePlayer().getBukkitPlayer();
        this.cityState = mangerCityStateMember.getCityState();
    }

    @Override
    public Inventory createInventory() {
        this.ownedPermissions = targetCityStateMember.getPermissions(); // 确保设置的和界面显示的一直（期间可能会被修改）

        PriorityConfigGUI.Builder guiBuilder = new PriorityConfigGUI.Builder();

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_BASIC);
        guiBuilder.fromConfig(thisGUISection, targetBukkitPlayer, new PlaceholderContainer().addCityStateMemberPlaceholders(targetCityStateMember));
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_BASIC);

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.back");
        guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.back"), targetBukkitPlayer), new ItemListener() {
            @Override
            public void onClick(InventoryClickEvent event) {
                if (canBack()) {
                    back();
                }
            }
        });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.back");

        // 如果管理者和目标玩家一样
        if (managerCityStateMember.equals(targetCityStateMember)) {
            return guiBuilder.build();
        }

        // 是会长或自己有权限且对方无踢人权限
        if (cityState.isOwner(managerCityStateMember) || managerCityStateMember.hasPermission(CityStatePermission.MEMBER_KICK) && !targetCityStateMember.hasPermission(CityStatePermission.MEMBER_KICK)) {
            PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.member_kick");
            guiBuilder.item(GUIItemManager.getPriorityItem(thisGUISection.getConfigurationSection("items.member_kick"), targetBukkitPlayer, new PlaceholderContainer().addCityStateMemberPlaceholders(targetCityStateMember)), new ItemListener() {
                @Override
                public void onClick(InventoryClickEvent event) {
                    if (!checkManagerPerOrReopen(CityStatePermission.MEMBER_KICK)) {
                        return;
                    }

                    new BaseConfirmGUI(CityStateMemberManageGUI.this
                            , cityStatePlayer
                            , thisGUISection.getConfigurationSection("items.member_kick.ConfirmGUI")
                            , new PlaceholderContainer().add("target", targetCityStateMember.getName())) {
                        @Override
                        public boolean canUse() {
                            return targetCityStateMember.isValid() && managerCityStateMember.isValid()
                                    && (cityState.isOwner(managerCityStateMember) ||
                                    (managerCityStateMember.hasPermission(CityStatePermission.MEMBER_KICK) && !targetCityStateMember.hasPermission(CityStatePermission.MEMBER_KICK)));
                        }

                        @Override
                        public void onCancel() {
                            back();
                        }

                        @Override
                        public void onConfirm() {
                            cityState.removeMember(targetCityStateMember);
                            back();
                        }
                    }.open();
                }
            });
            PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.member_kick");
        }

        if (managerCityStateMember.hasPermission(CityStatePermission.MANAGE_PERMISSION)) {
            setPermissionItem(guiBuilder, CityStatePermission.MEMBER_KICK);
            setPermissionItem(guiBuilder, CityStatePermission.SET_MEMBER_DAMAGE);
            setPermissionItem(guiBuilder, CityStatePermission.PLAYER_JOIN_CHECK);
            setPermissionItem(guiBuilder, CityStatePermission.SET_ANNOUNCEMENTS);
            setPermissionItem(guiBuilder, CityStatePermission.USE_SHOP);
            setPermissionItem(guiBuilder, CityStatePermission.USE_ICON_REPOSITORY);
        }

        return guiBuilder.build();
    }

    /**
     * 检查管理者权限，如果没权限则尝试重开GUI
     * @param cityStatePermission
     * @return
     */
    private boolean checkManagerPerOrReopen(@NotNull CityStatePermission cityStatePermission) {
        if (!managerCityStateMember.hasPermission(cityStatePermission) || targetCityStateMember.hasPermission(cityStatePermission)) {
            reopen();
            return false;
        }

        return true;
    }

    private void setPermissionItem(@NotNull PriorityConfigGUI.Builder guiBuilder, @NotNull CityStatePermission cityStatePermission) {
        guiBuilder.item(getPermissionItem(cityStatePermission), getPermissionItemListener(cityStatePermission));
    }

    private ItemListener getPermissionItemListener(@NotNull CityStatePermission cityStatePermission) {
        return new ItemListener() {
            @Override
            public void onClick(InventoryClickEvent event) {
                if (!checkManagerPerOrReopen(CityStatePermission.MANAGE_PERMISSION)) {
                    return;
                }

                targetCityStateMember.setPermission(cityStatePermission, !ownedPermissions.contains(cityStatePermission));
                reopen();
            }
        };
    }

    /**
     * 得到权限状态物品（give，take两种状态）
     */
    private PriorityItem getPermissionItem(@NotNull CityStatePermission cityStatePermission) {
        String path = "items.per_" + cityStatePermission.name().toLowerCase() + "." + (targetCityStateMember.hasPermission(cityStatePermission) ? "take" : "give");
        ConfigurationSection section = thisGUISection.getConfigurationSection(path);

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, path);
        PriorityItem priorityItem = GUIItemManager.getPriorityItem(section, targetBukkitPlayer, new PlaceholderContainer().addCityStateMemberPlaceholders(targetCityStateMember));;
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, path);
        return priorityItem;
    }

    @Override
    public boolean canUse() {
        if (!managerCityStateMember.isValid() || !targetCityStateMember.isValid()) {
            return false;
        }

        return managerCityStateMember.getPermissions().size() > 0;
    }
}
