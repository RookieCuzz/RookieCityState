package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.gui.BasePlayerGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.wishtree.WishRewardDefinition;
import com.cuzz.rookiecitystate.wishtree.WishTreeState;
import com.cuzz.rookiecitystate.wishtree.WishTreeView;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WishTreeGUI extends BasePlayerGUI {
    private final RookieCityState plugin = RookieCityState.inst();
    private final CityStateMember member;
    private final CityState cityState;
    private final ConfigurationSection config = plugin.getGUIYaml("WishTreeGUI");
    private boolean wishPending;

    public WishTreeGUI(@Nullable GUI lastGUI, @NotNull CityStateMember member) {
        super(lastGUI, Type.WISH_TREE, member.getCityStatePlayer());
        this.member = member;
        this.cityState = member.getCityState();
    }

    @Override public Inventory createInventory() {
        WishTreeView view = plugin.getWishTreeService().getView(getBukkitPlayer(), cityState);
        WishRewardDefinition target = plugin.getWishTreeService().getCatalog().get(view.targetRewardId());
        PlaceholderContainer placeholders = new PlaceholderContainer()
                .add("level", view.level()).add("experience", view.experience())
                .add("growth", view.weeklyGrowth()).add("target", view.weeklyTarget())
                .add("stones", view.magicStones()).add("wish_target", target == null ? "未选择" : target.displayName())
                .add("rare_pity", view.rarePity()).add("epic_pity", view.epicPity())
                .add("rare_limit", plugin.getWishTreeService().getCatalog().pityLimit(com.cuzz.rookiecitystate.wishtree.WishQuality.RARE))
                .add("epic_limit", plugin.getWishTreeService().getCatalog().pityLimit(com.cuzz.rookiecitystate.wishtree.WishQuality.EPIC))
                .add("free", view.freeWishesRemaining()).add("paid", view.paidWishesRemaining())
                .add("mailbox", view.mailboxSize());
        IndexConfigGUI.Builder builder = new IndexConfigGUI.Builder().fromConfig(config, getBukkitPlayer(), placeholders);
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.back"), getBukkitPlayer()), event -> back());
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.info"), getBukkitPlayer(), placeholders));
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.target"), getBukkitPlayer(), placeholders), event -> {
            close(); new WishTargetGUI(this, member).open();
        });
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.wish"), getBukkitPlayer(), placeholders), event -> {
            if (wishPending) return;
            wishPending = true;
            org.bukkit.entity.Player actor = getBukkitPlayer();
            plugin.getWishTreeService().wish(actor, cityState).whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    org.bukkit.entity.Player online = currentOnlinePlayer();
                    if (online == null) return;
                    if (error != null) Util.sendMsg(online, "&c许愿失败: " + error.getMessage());
                    else if (!result.success()) Util.sendMsg(online, "&c许愿失败: " + result.reason());
                    else Util.sendMsg(online, result.targetAwarded() ? "&d心愿实现！奖励已进入待领取箱。" : "&a许愿完成，奖励已进入待领取箱。");
                    if (isCurrentInstance()) reopen();
                } finally {
                    wishPending = false;
                }
            }));
        });
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.water"), getBukkitPlayer(), placeholders), event -> {
            org.bukkit.entity.Player actor = getBukkitPlayer();
            plugin.getWishTreeService().water(actor, cityState).whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                org.bukkit.entity.Player online = currentOnlinePlayer();
                if (online == null) return;
                if (error != null) Util.sendMsg(online, "&c浇水失败: " + error.getMessage());
                else if (!result.success()) Util.sendMsg(online, "&c" + result.reason());
                else Util.sendMsg(online, "&a浇水成功，周成长 " + result.growth() + "/" + result.target() + "。");
                if (isCurrentInstance()) reopen();
            }));
        });
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.inbox"), getBukkitPlayer(), placeholders), event -> {
            close(); new WishRewardInboxGUI(this, member).open();
        });
        for (int milestone : WishTreeState.MILESTONES) {
            builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.milestone_" + milestone), getBukkitPlayer()), event ->
                    plugin.getWishTreeService().claimWeekly(getBukkitPlayer(), cityState, milestone)
                            .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                                org.bukkit.entity.Player online = currentOnlinePlayer();
                                if (online == null) return;
                                if (error != null) Util.sendMsg(online, "&c领取失败: " + error.getMessage());
                                else Util.sendMsg(online, result.success() ? "&a周奖励已加入待领取箱。" : "&c" + result.reason());
                                if (isCurrentInstance()) reopen();
                            })));
        }
        return builder.build();
    }

    @Override public boolean canUse() { return member.isValid() && cityState.isWorldReady(); }
}
