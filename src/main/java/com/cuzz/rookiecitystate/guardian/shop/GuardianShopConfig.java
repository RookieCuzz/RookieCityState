package com.cuzz.rookiecitystate.guardian.shop;

import com.cuzz.rookiecitystate.guardian.GuardianForm;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class GuardianShopConfig {
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,48}");
    private static final Set<Particle> SAFE_PARTICLES = Set.of(
            Particle.FLAME, Particle.COMPOSTER, Particle.SNOWFLAKE, Particle.WITCH, Particle.END_ROD);
    private static final Map<GuardianForm, Set<String>> MODEL_ANIMATIONS = Map.of(
            GuardianForm.EGG, Set.of("idle", "interact", "eclode", "remove"),
            GuardianForm.BABY, Set.of("idle", "walk", "walk_loop", "idle_loop", "fly_start", "fly_loop", "fly_end", "death_animation"),
            GuardianForm.ADULT, Set.of("idle", "walk", "fly_start", "fly_loop", "fly_end", "spell_ground",
                    "spell_air", "plunge_start", "plunge_loop", "death_animation"));

    private final GuardianShopClock clock;
    private final int revision;
    private final int rotationSize;
    private final int retentionWeeks;
    private final long particleIntervalTicks;
    private final int particleCount;
    private final int particleMaxEmitters;
    private final double particleRadius;
    private final double particleHeight;
    private final double particleSpeed;
    private final double actionDistance;
    private final long actionCooldownMillis;
    private final Map<String, GuardianShopProduct> products;

    private GuardianShopConfig(GuardianShopClock clock, int revision, int rotationSize, int retentionWeeks,
                               long particleIntervalTicks, int particleCount, int particleMaxEmitters,
                               double particleRadius, double particleHeight, double particleSpeed,
                               double actionDistance, long actionCooldownMillis,
                               Map<String, GuardianShopProduct> products) {
        this.clock = clock;
        this.revision = revision;
        this.rotationSize = rotationSize;
        this.retentionWeeks = retentionWeeks;
        this.particleIntervalTicks = particleIntervalTicks;
        this.particleCount = particleCount;
        this.particleMaxEmitters = particleMaxEmitters;
        this.particleRadius = particleRadius;
        this.particleHeight = particleHeight;
        this.particleSpeed = particleSpeed;
        this.actionDistance = actionDistance;
        this.actionCooldownMillis = actionCooldownMillis;
        this.products = Map.copyOf(products);
    }

    public static GuardianShopConfig load(YamlConfiguration yaml) {
        int hour = yaml.getInt("rotation.reset_hour", -1);
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("rotation.reset_hour 必须为 0-23");
        GuardianShopClock clock = new GuardianShopClock(ZoneId.of(required(yaml, "rotation.timezone")), hour);
        int revision = positive(yaml, "schema_version");
        int size = positive(yaml, "rotation.size");
        int retention = positive(yaml, "rotation.retention_weeks");
        long interval = positive(yaml, "particle.interval_ticks");
        int count = positive(yaml, "particle.count");
        int maxEmitters = positive(yaml, "particle.max_emitters_per_run");
        double radius = finiteNonNegative(yaml, "particle.radius");
        double height = finiteNonNegative(yaml, "particle.height");
        double speed = finiteNonNegative(yaml, "particle.speed");
        double distance = finitePositive(yaml, "action.max_distance");
        long cooldown = Math.multiplyExact(positive(yaml, "action.cooldown_seconds"), 1000L);

        ConfigurationSection root = yaml.getConfigurationSection("products");
        if (root == null) throw new IllegalArgumentException("products 缺失");
        Map<String, GuardianShopProduct> products = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            if (!ID.matcher(id).matches()) throw new IllegalArgumentException("商品 ID 非法: " + id);
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            products.put(id, parseProduct(id, section, false));
        }
        if (products.size() < size) throw new IllegalArgumentException("启用商品少于轮换数量 " + size);
        return new GuardianShopConfig(clock, revision, size, retention, interval, count, maxEmitters,
                radius, height, speed, distance, cooldown, products);
    }

    static GuardianShopProduct parseProduct(String id, ConfigurationSection section, boolean snapshot) {
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException("商品 ID 非法: " + id);
        GuardianShopProductKind kind;
        try { kind = GuardianShopProductKind.valueOf(required(section, "kind").toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("商品类型非法: " + id, error); }
        long price = section.getLong("price", -1L);
        double weight = section.getDouble("weight", -1D);
        int limit = section.getInt("weekly_limit", kind.permanent() ? 1 : 0);
        int minLevel = section.getInt("minimum_guardian_level", 0);
        Material icon = Material.matchMaterial(required(section, "icon"));
        if (price < 1 || !Double.isFinite(weight) || weight <= 0D || limit < 1 || minLevel < 0 || minLevel > 5
                || icon == null || icon.isAir()) throw new IllegalArgumentException("商品通用字段非法: " + id);
        if (kind.permanent() && limit != 1) throw new IllegalArgumentException("永久商品 weekly_limit 必须为 1: " + id);

        Particle particle = null;
        String text = null;
        Map<GuardianForm, java.util.List<com.cuzz.rookiecitystate.guardian.GuardianAnimationStep>> animations = Map.of();
        Material rewardMaterial = null;
        int rewardAmount = 0;
        switch (kind) {
            case PARTICLE -> {
                try { particle = Particle.valueOf(required(section, "particle").toUpperCase(java.util.Locale.ROOT)); }
                catch (IllegalArgumentException error) { throw new IllegalArgumentException("粒子非法: " + id, error); }
                if (!SAFE_PARTICLES.contains(particle)) throw new IllegalArgumentException("粒子需要额外数据或未列入安全清单: " + particle);
            }
            case TITLE, CHAT_PREFIX -> {
                text = required(section, "text");
                if (text.length() > 48 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
                    throw new IllegalArgumentException("文本商品长度或换行非法: " + id);
                }
            }
            case ACTION -> {
                animations = GuardianShopProduct.loadAnimations(section);
                if (animations.isEmpty()) throw new IllegalArgumentException("动作没有动画序列: " + id);
                animations.forEach((form, steps) -> steps.forEach(step -> {
                    if (!MODEL_ANIMATIONS.get(form).contains(step.animation())) {
                        throw new IllegalArgumentException("动作 " + id + " 在 " + form + " 使用了不存在的公共动画: " + step.animation());
                    }
                }));
            }
            case ITEM -> {
                rewardMaterial = Material.matchMaterial(required(section, "reward.material"));
                rewardAmount = section.getInt("reward.amount", 0);
                if (rewardMaterial == null || rewardMaterial.isAir() || !rewardMaterial.isItem()
                        || rewardAmount < 1 || rewardAmount > rewardMaterial.getMaxStackSize()) {
                    throw new IllegalArgumentException("装饰物奖励非法: " + id);
                }
            }
            case MAGIC_STONE -> {
                rewardAmount = section.getInt("reward.amount", 0);
                if (rewardAmount < 1 || rewardAmount > 1000) throw new IllegalArgumentException("魔力石数量非法: " + id);
            }
        }
        return new GuardianShopProduct(id, required(section, "display_name"), kind, price, weight, limit,
                minLevel, icon, particle, text, animations, rewardMaterial, rewardAmount);
    }

    private static int positive(YamlConfiguration yaml, String path) {
        int value = yaml.getInt(path, 0);
        if (value < 1) throw new IllegalArgumentException(path + " 必须为正整数");
        return value;
    }
    private static double finiteNonNegative(YamlConfiguration yaml, String path) {
        double value = yaml.getDouble(path, -1D);
        if (!Double.isFinite(value) || value < 0D) throw new IllegalArgumentException(path + " 不能为负数");
        return value;
    }
    private static double finitePositive(YamlConfiguration yaml, String path) {
        double value = finiteNonNegative(yaml, path);
        if (value == 0D) throw new IllegalArgumentException(path + " 必须为正数");
        return value;
    }
    private static String required(ConfigurationSection section, String path) {
        return GuardianShopProduct.required(section, path);
    }

    public GuardianShopClock clock() { return clock; }
    public int revision() { return revision; }
    public int rotationSize() { return rotationSize; }
    public int retentionWeeks() { return retentionWeeks; }
    public long particleIntervalTicks() { return particleIntervalTicks; }
    public int particleCount() { return particleCount; }
    public int particleMaxEmitters() { return particleMaxEmitters; }
    public double particleRadius() { return particleRadius; }
    public double particleHeight() { return particleHeight; }
    public double particleSpeed() { return particleSpeed; }
    public double actionDistance() { return actionDistance; }
    public long actionCooldownMillis() { return actionCooldownMillis; }
    public Map<String, GuardianShopProduct> products() { return products; }
    public GuardianShopProduct product(String id) { return products.get(id); }
}
