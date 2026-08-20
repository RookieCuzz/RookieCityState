package com.cuzz.rookiecitystate.gui;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.gui.entities.MainGUI;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.util.Util;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Optional;
import org.bukkit.Bukkit;

/**
 * 采用被动式，容错式更新设计，每次点击如果遇到无效的情况则强制更新，否则继续使用
 */
public interface GUI {
    enum Type {
        CREATE,
        INFO,
        MEMBER_LIST,
        MINE,
        MAIN,
        PLAYER_JOIN_CHECK,
        MEMBER_MANAGE,
        DONATE,
        SHOP,
        CONFIRM,
        SHOP_CONFIRM,
        ICON_REPOSITORY,
        WISH_TREE,
        WISH_TARGET,
        WISH_INBOX,
        GUARDIAN_BEAST,
        GUARDIAN_SPECIES,
        GUARDIAN_SHOP,
        GUARDIAN_SHOP_CONFIRM,
        GUARDIAN_LOCKER,
        POPULAR_CITY_STATE,
        CITY_LIKE_CONFIRM,
        BAG,
        WAR
    }

    /**
     * 决定GUI能否被看到
     * @return
     */
    boolean canUse();

    GUI getLastGUI();

    CityStatePlayer getCityStatePlayer();

    Inventory createInventory();

    Type getGUIType();

    default Player getBukkitPlayer() {
        return getCityStatePlayer().getBukkitPlayer();
    }

    default Player currentOnlinePlayer() {
        Player player = Bukkit.getPlayer(getCityStatePlayer().getUuid());
        return player != null && player.isOnline() ? player : null;
    }

    default boolean isCurrentInstance() {
        return getCityStatePlayer().getUsingGUI() == this;
    }

    default void openLater(long tick) {
        new BukkitRunnable() {
            @Override
            public void run() {
                open();
            }
        }.runTaskLater(RookieCityState.inst(), tick);
    }

    default void open() {
        // 检查能否使用
        if (!canUse()) {
            Util.sendMsg(getBukkitPlayer(), "&f当前 GUI 暂时无法使用.");

            GUI lastGUI = getLastGUI();

            if (lastGUI != null) {
                lastGUI.open();
            } else {
                new MainGUI(getCityStatePlayer()).open();
            }

            return;
        }

        String className = this.getClass().getSimpleName();

        if (className.equalsIgnoreCase("")) {
            className = this.getClass().getTypeName();
        }

        PluginLogger.debug("=== 尝试创建GUI " + className + " ===");

        Inventory inventory = createInventory();

        if (inventory == null) {
            throw new RuntimeException("getInventory() 不能返回 null");
        }

        PluginLogger.debug("=== 创建GUI " + className + " 完毕 ===");

        getCityStatePlayer().getBukkitPlayer().openInventory(inventory);
        getCityStatePlayer().setUsingGUI(this);

        PluginLogger.debug("玩家 '" + getCityStatePlayer().getName() + "' 打开了 GUI '" + className + "'.");
    }

    default boolean canBack() {
        return getLastGUI() != null;
    }

    default void back() {
        close();

        GUI lastGUI = Optional.ofNullable(getLastGUI()).orElseThrow(() -> new RuntimeException("没有上一个GUI了"));

        lastGUI.open();
    }

    /**
     * 先关闭等待later秒后再返回
     * @param later
     */
    default void back(long later) {
        close();

        GUI lastGUI = Optional.ofNullable(getLastGUI()).orElseThrow(() -> new RuntimeException("没有上一个GUI了"));

        new BukkitRunnable() {
            @Override
            public void run() {
                lastGUI.open();
            }
        }.runTaskLater(RookieCityState.inst(), later);
    }

    default void close() {
        if (!this.equals(getCityStatePlayer().getUsingGUI())) {
            throw new RuntimeException("当前GUI没在使用");
        }

        getCityStatePlayer().closeInventory();
    }

    /**
     * 关闭，打开
     */

    default void reopen() {
        close();
        open();
    }

    /**
     * 关闭，延时，打开
     * @param later
     */
    default void reopen(long later) {
        close();

        new BukkitRunnable() {
            @Override
            public void run() {
                open();
            }
        }.runTaskLater(RookieCityState.inst(), later);
    }
}
