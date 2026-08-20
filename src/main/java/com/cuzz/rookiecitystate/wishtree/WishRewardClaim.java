package com.cuzz.rookiecitystate.wishtree;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;

public record WishRewardClaim(
        UUID id,
        String rewardId,
        String displayName,
        WishQuality quality,
        boolean targetable,
        int minimumTreeLevel,
        double weight,
        String source,
        UUID cityStateId,
        long createdAt,
        WishClaimState state,
        List<WishRewardAction> actions
) {
    public WishRewardClaim { actions = List.copyOf(actions); }

    static WishRewardClaim load(UUID id, ConfigurationSection section) {
        if (section == null) throw new IllegalArgumentException("奖励记录为空");
        List<WishRewardAction> actions = new ArrayList<>();
        ConfigurationSection root = section.getConfigurationSection("actions");
        if (root != null) root.getKeys(false).stream().sorted(Comparator.comparingInt(Integer::parseInt)).forEach(key -> {
            ConfigurationSection action = root.getConfigurationSection(key);
            if (action == null) throw new IllegalArgumentException("奖励 action 为空: " + key);
            WishRewardType type = WishRewardType.valueOf(require(action.getString("type"), "action.type"));
            double amount = action.getDouble("amount");
            if (!Double.isFinite(amount) || amount <= 0D) throw new IllegalArgumentException("奖励数值必须为正数");
            String material = action.getString("material");
            if (type == WishRewardType.ITEM && (material == null || Material.matchMaterial(material) == null
                    || amount != Math.rint(amount) || amount > Integer.MAX_VALUE)) {
                throw new IllegalArgumentException("奖励物品材质或数量无效");
            }
            List<String> commands = action.getStringList("commands");
            if (type == WishRewardType.COMMANDS && commands.stream().noneMatch(command -> !command.isBlank())) {
                throw new IllegalArgumentException("指令奖励不能为空");
            }
            actions.add(new WishRewardAction(type, material, amount, commands));
        });
        if (actions.isEmpty()) throw new IllegalArgumentException("奖励 actions 不能为空");
        String city = section.getString("city_state_uuid");
        String rewardId = require(section.getString("reward_id"), "reward_id");
        String source = require(section.getString("source"), "source");
        long createdAt = section.getLong("created_at");
        if (createdAt <= 0L) throw new IllegalArgumentException("created_at 无效");
        return new WishRewardClaim(id, rewardId, section.getString("display_name", "奖励"),
                WishQuality.valueOf(section.getString("quality", WishQuality.COMMON.name())),
                section.getBoolean("targetable"), section.getInt("minimum_tree_level", 1),
                section.getDouble("weight"),
                source, city == null ? null : UUID.fromString(city), createdAt,
                WishClaimState.valueOf(require(section.getString("state"), "state")), actions);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return value;
    }
}
