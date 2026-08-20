package com.cuzz.rookiecitystate.wishtree;

import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class PlayerWishData {
    private static final int MAILBOX_LIMIT = 200;
    private final CityStatePlayer player;
    private final ConfigurationSection root;

    PlayerWishData(CityStatePlayer player) {
        this.player = player;
        ConfigurationSection existing = player.getYaml().getConfigurationSection("wish_tree");
        this.root = existing == null ? player.getYaml().createSection("wish_tree") : existing;
        ConfigurationSection mailbox = root.getConfigurationSection("mailbox");
        boolean recovered = false;
        if (mailbox != null) {
            for (String claimId : mailbox.getKeys(false)) {
                String path = claimId + ".state";
                if (WishClaimState.DISPATCHING.name().equals(mailbox.getString(path))) {
                    mailbox.set(path, WishClaimState.AMBIGUOUS.name());
                    mailbox.set(claimId + ".updated_at", System.currentTimeMillis());
                    recovered = true;
                }
            }
        }
        if (recovered) player.save();
    }

    void ensureDay(String day) {
        if (day.equals(root.getString("daily.cycle"))) return;
        root.set("daily", null);
        root.set("daily.cycle", day);
        root.set("daily.free_used", 0);
        root.set("daily.paid_used", 0);
    }

    int stones() { return root.getInt("magic_stones"); }
    void addStones(int amount) {
        long next = (long) stones() + amount;
        if (next < 0 || next > Integer.MAX_VALUE) throw new IllegalArgumentException("魔力石余额非法");
        root.set("magic_stones", (int) next);
    }
    int freeUsed() { return root.getInt("daily.free_used"); }
    int paidUsed() { return root.getInt("daily.paid_used"); }
    void consumeWish() {
        if (freeUsed() < 1) root.set("daily.free_used", freeUsed() + 1);
        else {
            if (paidUsed() >= 5) throw new IllegalStateException("今日额外许愿次数已用完");
            if (stones() < 1) throw new IllegalStateException("魔力石不足");
            addStones(-1);
            root.set("daily.paid_used", paidUsed() + 1);
        }
    }

    String targetId() { return root.getString("target.reward_id"); }
    WishQuality targetQuality() {
        String value = root.getString("target.quality");
        return value == null ? null : WishQuality.valueOf(value);
    }
    void target(WishRewardDefinition reward) {
        root.set("target.reward_id", reward.id());
        root.set("target.quality", reward.quality().name());
    }
    void clearTarget() { root.set("target", null); }
    int pity(WishQuality quality) { return root.getInt("pity." + quality.name()); }
    void pity(WishQuality quality, int value) { root.set("pity." + quality.name(), Math.max(0, value)); }

    boolean mailboxFull() { return mailbox().size() >= MAILBOX_LIMIT; }
    int mailboxSize() { return mailbox().size(); }
    int mailboxRemaining() { return MAILBOX_LIMIT - mailboxSize(); }
    List<WishRewardClaim> mailbox() {
        ConfigurationSection box = root.getConfigurationSection("mailbox");
        if (box == null) return List.of();
        List<WishRewardClaim> claims = new ArrayList<>();
        boolean quarantined = false;
        for (String key : new ArrayList<>(box.getKeys(false))) {
            try { claims.add(WishRewardClaim.load(UUID.fromString(key), box.getConfigurationSection(key))); }
            catch (RuntimeException error) {
                ConfigurationSection source = box.getConfigurationSection(key);
                String target = "mailbox_quarantine." + key;
                root.set(target, source == null ? java.util.Map.of("raw", String.valueOf(box.get(key)))
                        : new java.util.LinkedHashMap<>(source.getValues(true)));
                root.set(target + ".quarantine_reason", error.getClass().getSimpleName() + ": " + error.getMessage());
                root.set(target + ".quarantined_at", System.currentTimeMillis());
                box.set(key, null);
                quarantined = true;
            }
        }
        if (quarantined) player.save();
        return claims.stream().filter(claim -> claim.state() != WishClaimState.CLAIMED)
                .sorted(Comparator.comparingLong(WishRewardClaim::createdAt)).toList();
    }

    UUID enqueue(WishRewardDefinition reward, String source, UUID cityStateId) {
        if (mailboxFull()) throw new IllegalStateException("待领取奖励箱已满");
        UUID id = UUID.randomUUID();
        String path = "mailbox." + id;
        root.set(path + ".reward_id", reward.id());
        root.set(path + ".display_name", reward.displayName());
        root.set(path + ".quality", reward.quality().name());
        root.set(path + ".targetable", reward.targetable());
        root.set(path + ".minimum_tree_level", reward.minimumTreeLevel());
        root.set(path + ".weight", reward.weight());
        root.set(path + ".source", source);
        root.set(path + ".city_state_uuid", cityStateId == null ? null : cityStateId.toString());
        root.set(path + ".created_at", System.currentTimeMillis());
        root.set(path + ".state", WishClaimState.READY.name());
        int index = 0;
        for (WishRewardAction action : reward.actions()) {
            String actionPath = path + ".actions." + index++;
            root.set(actionPath + ".type", action.type().name());
            root.set(actionPath + ".material", action.material());
            root.set(actionPath + ".amount", action.amount());
            root.set(actionPath + ".commands", action.commands());
        }
        return id;
    }

    WishRewardClaim claim(UUID claimId) {
        ConfigurationSection section = root.getConfigurationSection("mailbox." + claimId);
        return section == null ? null : WishRewardClaim.load(claimId, section);
    }
    void claimState(UUID claimId, WishClaimState state) {
        root.set("mailbox." + claimId + ".state", state.name());
        root.set("mailbox." + claimId + ".updated_at", System.currentTimeMillis());
    }

    boolean weeklyClaimed(String week, UUID cityId, int milestone) {
        return root.getIntegerList("weekly_claims." + week + "." + cityId).contains(milestone);
    }
    void markWeeklyClaimed(String week, UUID cityId, int milestone) {
        String path = "weekly_claims." + week + "." + cityId;
        Set<Integer> values = new LinkedHashSet<>(root.getIntegerList(path));
        values.add(milestone);
        root.set(path, values.stream().toList());
        ConfigurationSection section = root.getConfigurationSection("weekly_claims");
        if (section != null) for (String key : new ArrayList<>(section.getKeys(false))) if (!key.equals(week)) section.set(key, null);
    }

    boolean grantSignStoneOnce(String day) {
        if (day.equals(root.getString("last_sign_stone_day"))) return false;
        addStones(1);
        root.set("last_sign_stone_day", day);
        return true;
    }

    void resetDaily(String day) {
        root.set("daily", null);
        ensureDay(day);
    }
    void resetPity(WishQuality quality) { pity(quality, 0); }
    void save() { player.save(); }
}
