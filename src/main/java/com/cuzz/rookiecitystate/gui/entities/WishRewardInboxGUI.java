package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.gui.BasePageableGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.internal.item.ItemBuilder;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.wishtree.WishClaimState;
import com.cuzz.rookiecitystate.wishtree.WishRewardClaim;
import com.cuzz.rookiecitystate.wishtree.WishRewardType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class WishRewardInboxGUI extends BasePageableGUI {
    private static final int PAGE_SIZE = 28;
    private final RookieCityState plugin = RookieCityState.inst();
    private final CityStateMember member;
    private final ConfigurationSection config = plugin.getGUIYaml("WishRewardInboxGUI");
    private List<WishRewardClaim> claims = List.of();

    public WishRewardInboxGUI(@Nullable GUI lastGUI, @NotNull CityStateMember member) {
        super(lastGUI, Type.WISH_INBOX, member.getCityStatePlayer()); this.member = member;
    }
    @Override public void update() {
        claims = plugin.getWishTreeService().mailbox(member.getCityStatePlayer());
        setPageCount((claims.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }
    @Override public Inventory createInventory() {
        PlaceholderContainer page = new PlaceholderContainer()
                .add("page", getCurrentPage() < 0 ? 0 : getCurrentPage() + 1)
                .add("total_page", getPageCount());
        IndexConfigGUI.Builder builder = new IndexConfigGUI.Builder().fromConfig(config, getBukkitPlayer(), page);
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.back"), getBukkitPlayer()), event -> back());
        if (hasPreciousPage()) builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.previous"), getBukkitPlayer()), event -> previousPage());
        if (hasNextPage()) builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.next"), getBukkitPlayer()), event -> nextPage());
        builder.item(GUIItemManager.getIndexItem(config.getConfigurationSection("items.claim_all"), getBukkitPlayer()), event -> claimAllSafe());
        List<Integer> slots = Util.getIndexes(config.getString("indexes"));
        if (getCurrentPage() >= 0) {
            int start = getCurrentPage() * PAGE_SIZE;
            for (int i = start; i < Math.min(start + PAGE_SIZE, claims.size()); i++) {
                WishRewardClaim claim = claims.get(i);
                Material material = claim.state() == WishClaimState.AMBIGUOUS ? Material.BARRIER : Material.CHEST_MINECART;
                builder.item(slots.get(i - start), new ItemBuilder().material(material).colored()
                        .displayName(claim.displayName()).lores(List.of("",
                                "&8▪ &7品质：&f" + qualityName(claim.quality().name()),
                                "&8▪ &7来源：&f" + sourceName(claim.source()),
                                "&8▪ &7状态：&f" + stateName(claim.state()),
                                "", claim.state() == WishClaimState.AMBIGUOUS
                                        ? "&c⚠ 状态待管理员核验，请勿重复领取"
                                        : "&e▶ 点击领取奖励")).build(), event -> claim(claim));
            }
        }
        return builder.build();
    }
    private static String qualityName(String quality) {
        return switch (quality) {
            case "COMMON" -> "普通";
            case "RARE" -> "&b稀有";
            case "EPIC" -> "&d史诗";
            default -> quality;
        };
    }
    private static String stateName(WishClaimState state) {
        return switch (state) {
            case READY -> "&a待领取";
            case DISPATCHING -> "&e发放中";
            case AMBIGUOUS -> "&c待核验";
            case CLAIMED -> "&7已领取";
        };
    }
    private static String sourceName(String source) {
        return switch (source == null ? "" : source.toUpperCase(java.util.Locale.ROOT)) {
            case "WISH" -> "许愿";
            case "TARGET" -> "心愿达成";
            case "WEEKLY", "WEEKLY_MILESTONE" -> "每周成长奖励";
            default -> source == null || source.isBlank() ? "未知" : source;
        };
    }
    private void claim(WishRewardClaim claim) {
        org.bukkit.entity.Player actor = getBukkitPlayer();
        plugin.getWishTreeService().claim(actor, claim.id()).whenComplete((result, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    org.bukkit.entity.Player online = currentOnlinePlayer();
                    if (online == null) return;
                    if (error != null) Util.sendMsg(online, "&c领取失败: " + error.getMessage());
                    else Util.sendMsg(online, result.success() ? "&a领取成功。" : "&c" + result.reason());
                    if (isCurrentInstance()) reopen();
                }));
    }
    private void claimAllSafe() {
        List<WishRewardClaim> safe = claims.stream().filter(claim -> claim.state() == WishClaimState.READY)
                .filter(claim -> claim.actions().stream().allMatch(action -> action.type() == WishRewardType.MAGIC_STONE)).toList();
        for (WishRewardClaim claim : safe) plugin.getWishTreeService().claim(getBukkitPlayer(), claim.id());
        Util.sendMsg(getBukkitPlayer(), "&a已尝试领取 " + safe.size() + " 份安全奖励。");
        reopen(1L);
    }
    @Override public boolean canUse() { return member.isValid(); }
}
