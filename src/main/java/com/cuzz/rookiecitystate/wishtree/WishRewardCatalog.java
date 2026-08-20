package com.cuzz.rookiecitystate.wishtree;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class WishRewardCatalog {
    private final Map<String, WishRewardDefinition> rewards;
    private final List<WishRewardDefinition> baseRewards;
    private final Map<Integer, WeeklyReward> weeklyRewards;
    private final int rarePity;
    private final int epicPity;
    private final double rareChance;
    private final double epicChance;

    private WishRewardCatalog(Map<String, WishRewardDefinition> rewards,
                              List<WishRewardDefinition> baseRewards,
                              Map<Integer, WeeklyReward> weeklyRewards,
                              int rarePity, int epicPity, double rareChance, double epicChance) {
        this.rewards = Map.copyOf(rewards);
        this.baseRewards = List.copyOf(baseRewards);
        this.weeklyRewards = Map.copyOf(weeklyRewards);
        this.rarePity = rarePity;
        this.epicPity = epicPity;
        this.rareChance = rareChance;
        this.epicChance = epicChance;
    }

    public static WishRewardCatalog load(YamlConfiguration yaml) {
        Objects.requireNonNull(yaml, "奖励配置不能为空");
        ConfigurationSection root = Objects.requireNonNull(yaml.getConfigurationSection("rewards"),
                "wish_tree_rewards.yml 缺少 rewards");
        Map<String, WishRewardDefinition> all = new LinkedHashMap<>();
        List<WishRewardDefinition> base = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = Objects.requireNonNull(root.getConfigurationSection(id), "无效奖励: " + id);
            String pool = section.getString("pool", "TARGET").toUpperCase(Locale.ROOT);
            WishQuality quality = parseEnum(WishQuality.class, section.getString("quality", "COMMON"), id + ".quality");
            boolean targetable = section.getBoolean("targetable", pool.equals("TARGET"));
            int minimumLevel = section.getInt("minimum_tree_level", 1);
            double weight = section.getDouble("weight", 0D);
            if (minimumLevel < 1 || minimumLevel > 5) throw new IllegalArgumentException(id + " 的 minimum_tree_level 必须为 1-5");
            if (!Double.isFinite(weight) || weight < 0D) throw new IllegalArgumentException(id + " 的 weight 非法");
            if (pool.equals("BASE") && weight <= 0D) throw new IllegalArgumentException(id + " 的基础池 weight 必须大于 0");
            if (targetable && quality == WishQuality.COMMON) throw new IllegalArgumentException(id + " 的目标品质不能为 COMMON");
            List<WishRewardAction> actions = parseActions(section, id);
            WishRewardDefinition definition = new WishRewardDefinition(id,
                    section.getString("display_name", id), quality, targetable, minimumLevel, weight, actions);
            if (all.put(id, definition) != null) throw new IllegalArgumentException("奖励 ID 重复: " + id);
            if (pool.equals("BASE")) base.add(definition);
            else if (!pool.equals("TARGET")) throw new IllegalArgumentException(id + " 的 pool 只能为 BASE 或 TARGET");
        }
        if (base.isEmpty()) throw new IllegalArgumentException("基础许愿奖池不能为空");
        if (all.values().stream().noneMatch(WishRewardDefinition::targetable)) {
            throw new IllegalArgumentException("至少需要一个可选心愿目标");
        }

        Map<Integer, WeeklyReward> weekly = new LinkedHashMap<>();
        ConfigurationSection weeklyRoot = Objects.requireNonNull(yaml.getConfigurationSection("weekly_rewards"),
                "wish_tree_rewards.yml 缺少 weekly_rewards");
        for (int milestone : List.of(25, 50, 75, 100)) {
            ConfigurationSection section = Objects.requireNonNull(weeklyRoot.getConfigurationSection(String.valueOf(milestone)),
                    "缺少周奖励档位 " + milestone);
            List<String> rewardIds = section.getStringList("rewards");
            if (rewardIds.isEmpty()) throw new IllegalArgumentException("周奖励 " + milestone + " 不能为空");
            rewardIds.forEach(id -> {
                if (!all.containsKey(id)) throw new IllegalArgumentException("周奖励引用未知 rewardId: " + id);
            });
            double cityMoney = section.getDouble("city_gmoney", 0D);
            if (!Double.isFinite(cityMoney) || cityMoney < 0D) throw new IllegalArgumentException("周奖励城邦币非法: " + milestone);
            weekly.put(milestone, new WeeklyReward(rewardIds, cityMoney));
        }
        int rarePity = yaml.getInt("settings.pity.rare", 30);
        int epicPity = yaml.getInt("settings.pity.epic", 80);
        double rareChance = yaml.getDouble("settings.early_chance.rare", 0.025D);
        double epicChance = yaml.getDouble("settings.early_chance.epic", 0.005D);
        if (rarePity < 1 || epicPity < 1) throw new IllegalArgumentException("保底次数必须大于 0");
        if (!validChance(rareChance) || !validChance(epicChance)) throw new IllegalArgumentException("提前命中率必须在 0-1 之间");
        return new WishRewardCatalog(all, base, weekly, rarePity, epicPity, rareChance, epicChance);
    }

    private static List<WishRewardAction> parseActions(ConfigurationSection reward, String id) {
        ConfigurationSection actions = Objects.requireNonNull(reward.getConfigurationSection("actions"), id + " 缺少 actions");
        List<WishRewardAction> result = new ArrayList<>();
        for (String key : actions.getKeys(false)) {
            ConfigurationSection action = Objects.requireNonNull(actions.getConfigurationSection(key), id + ".actions." + key + " 无效");
            WishRewardType type = parseEnum(WishRewardType.class, action.getString("type"), id + ".actions." + key + ".type");
            double amount = action.getDouble("amount", 0D);
            String material = action.getString("material");
            List<String> commands = action.getStringList("commands");
            if (!Double.isFinite(amount) || amount < 0D) throw new IllegalArgumentException(id + " 的奖励数量非法");
            if (type == WishRewardType.ITEM) {
                Material parsed = material == null ? null : Material.matchMaterial(material);
                if (parsed == null || parsed.isAir() || amount < 1 || amount > Integer.MAX_VALUE
                        || amount != Math.rint(amount)) {
                    throw new IllegalArgumentException(id + " 的 ITEM 奖励无效");
                }
            } else if (type == WishRewardType.COMMANDS) {
                if (commands.isEmpty() || commands.stream().anyMatch(String::isBlank)) {
                    throw new IllegalArgumentException(id + " 的 COMMANDS 不能为空");
                }
            } else if (amount <= 0D || ((type == WishRewardType.PLAYER_POINTS || type == WishRewardType.MAGIC_STONE)
                    && (amount > Integer.MAX_VALUE || amount != Math.rint(amount)))) {
                throw new IllegalArgumentException(id + " 的奖励数量必须为正数");
            }
            result.add(new WishRewardAction(type, material, amount, commands));
        }
        if (result.isEmpty()) throw new IllegalArgumentException(id + " 至少需要一个奖励动作");
        return result;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String path) {
        try { return Enum.valueOf(type, Objects.requireNonNull(raw, path + " 缺失").toUpperCase(Locale.ROOT)); }
        catch (RuntimeException error) { throw new IllegalArgumentException(path + " 无效: " + raw, error); }
    }

    public WishRewardDefinition get(String id) { return id == null ? null : rewards.get(id); }
    public List<WishRewardDefinition> baseRewards() { return baseRewards; }
    public List<WishRewardDefinition> targets(int level) {
        return rewards.values().stream().filter(WishRewardDefinition::targetable)
                .filter(reward -> reward.minimumTreeLevel() <= level).toList();
    }
    public WeeklyReward weekly(int milestone) { return weeklyRewards.get(milestone); }
    public Map<String, WishRewardDefinition> rewards() { return Collections.unmodifiableMap(rewards); }
    public int pityLimit(WishQuality quality) { return quality == WishQuality.RARE ? rarePity : epicPity; }
    public double earlyChance(WishQuality quality) { return quality == WishQuality.RARE ? rareChance : epicChance; }

    private static boolean validChance(double value) { return Double.isFinite(value) && value >= 0D && value <= 1D; }

    public record WeeklyReward(List<String> rewardIds, double cityGmoney) {
        public WeeklyReward { rewardIds = List.copyOf(rewardIds); }
    }
}
