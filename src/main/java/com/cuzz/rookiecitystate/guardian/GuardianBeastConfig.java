package com.cuzz.rookiecitystate.guardian;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class GuardianBeastConfig {
    private final GuardianClock clock;
    private final int maxFeeds;
    private final int target;
    private final int favoriteCount;
    private final int favoriteMultiplier;
    private final List<Integer> thresholds;
    private final int modelRevision;
    private final String eggModel;
    private final Map<Material, GuardianFood> foods;
    private final Map<GuardianSpecies, GuardianSpeciesDefinition> species;
    private final double anchorX, anchorY, anchorZ;
    private final float anchorYaw;

    private GuardianBeastConfig(GuardianClock clock, int maxFeeds, int target, int favoriteCount,
                                int favoriteMultiplier, List<Integer> thresholds, int modelRevision,
                                String eggModel, Map<Material, GuardianFood> foods,
                                Map<GuardianSpecies, GuardianSpeciesDefinition> species,
                                double anchorX, double anchorY, double anchorZ, float anchorYaw) {
        this.clock = clock;
        this.maxFeeds = maxFeeds;
        this.target = target;
        this.favoriteCount = favoriteCount;
        this.favoriteMultiplier = favoriteMultiplier;
        this.thresholds = List.copyOf(thresholds);
        this.modelRevision = modelRevision;
        this.eggModel = eggModel;
        this.foods = Collections.unmodifiableMap(new LinkedHashMap<>(foods));
        this.species = Collections.unmodifiableMap(new EnumMap<>(species));
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.anchorYaw = anchorYaw;
    }

    public static GuardianBeastConfig load(YamlConfiguration yaml) {
        Objects.requireNonNull(yaml, "yaml");
        String zoneName = required(yaml, "reset.timezone");
        int resetHour = yaml.getInt("reset.hour", -1);
        if (resetHour < 0 || resetHour > 23) throw new IllegalArgumentException("reset.hour 必须为 0-23");
        int maxFeeds = positive(yaml, "daily.max_feeds");
        int target = positive(yaml, "daily.target");
        int favoriteCount = positive(yaml, "daily.favorite_count");
        int multiplier = positive(yaml, "daily.favorite_multiplier");
        List<Integer> thresholds = yaml.getIntegerList("growth.completed_day_thresholds");
        if (thresholds.size() != 5) throw new IllegalArgumentException("growth.completed_day_thresholds 必须包含五项");
        int previous = -1;
        for (int threshold : thresholds) {
            if (threshold <= previous) throw new IllegalArgumentException("成长门槛必须严格递增");
            previous = threshold;
        }
        int revision = positive(yaml, "models.revision");
        if (revision != 1) throw new IllegalArgumentException("当前版本只内置模型修订 r1");
        String eggModel = required(yaml, "models.egg");

        Map<Material, GuardianFood> foods = new LinkedHashMap<>();
        ConfigurationSection foodSection = Objects.requireNonNull(yaml.getConfigurationSection("foods"), "foods 缺失");
        for (String key : foodSection.getKeys(false)) {
            ConfigurationSection entry = foodSection.getConfigurationSection(key);
            if (entry == null || !entry.getBoolean("enabled", true)) continue;
            Material material = Material.matchMaterial(entry.getString("material", key));
            if (material == null || material.isAir()) throw new IllegalArgumentException("无效食物材质: " + key);
            if (foods.put(material, new GuardianFood(material, entry.getInt("nourishment"),
                    entry.getInt("contribution"), true)) != null) {
                throw new IllegalArgumentException("重复食物材质: " + material);
            }
        }
        if (foods.size() < favoriteCount) throw new IllegalArgumentException("启用食物数量少于每日喜爱食物数量");

        Map<GuardianSpecies, GuardianSpeciesDefinition> definitions = new EnumMap<>(GuardianSpecies.class);
        for (GuardianSpecies type : GuardianSpecies.values()) {
            String path = "species." + type.id();
            Material icon = Material.matchMaterial(required(yaml, path + ".icon"));
            if (icon == null || icon.isAir()) throw new IllegalArgumentException("无效灵兽图标: " + path);
            definitions.put(type, new GuardianSpeciesDefinition(type, required(yaml, path + ".name"), icon,
                    required(yaml, path + ".baby_model"), required(yaml, path + ".adult_model")));
        }
        return new GuardianBeastConfig(new GuardianClock(ZoneId.of(zoneName), resetHour), maxFeeds, target,
                favoriteCount, multiplier, thresholds, revision, eggModel, foods, definitions,
                yaml.getDouble("visual.anchor.x", -24.5), yaml.getDouble("visual.anchor.y", 65.0),
                yaml.getDouble("visual.anchor.z", 4.5), (float) yaml.getDouble("visual.anchor.yaw", 0.0));
    }

    private static int positive(YamlConfiguration yaml, String path) {
        int value = yaml.getInt(path, 0);
        if (value < 1) throw new IllegalArgumentException(path + " 必须为正整数");
        return value;
    }

    private static String required(YamlConfiguration yaml, String path) {
        String value = yaml.getString(path);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(path + " 不能为空");
        return value.trim();
    }

    public String day(long now) { return clock.day(now); }
    public int maxFeeds() { return maxFeeds; }
    public int target() { return target; }
    public int favoriteCount() { return favoriteCount; }
    public int favoriteMultiplier() { return favoriteMultiplier; }
    public List<Integer> thresholds() { return thresholds; }
    public int modelRevision() { return modelRevision; }
    public String eggModel() { return eggModel; }
    public Map<Material, GuardianFood> foods() { return foods; }
    public GuardianFood food(Material material) { return foods.get(material); }
    public GuardianSpeciesDefinition species(GuardianSpecies type) { return species.get(type); }
    public Map<GuardianSpecies, GuardianSpeciesDefinition> species() { return species; }
    public double anchorX() { return anchorX; }
    public double anchorY() { return anchorY; }
    public double anchorZ() { return anchorZ; }
    public float anchorYaw() { return anchorYaw; }

    public int level(int completedDays) {
        int level = 0;
        for (int threshold : thresholds) if (completedDays >= threshold) level++;
        return level;
    }

    public GuardianForm form(int completedDays) {
        int level = level(completedDays);
        return level == 0 ? GuardianForm.EGG : level < 3 ? GuardianForm.BABY : GuardianForm.ADULT;
    }

    public String model(GuardianSpecies selected, int completedDays) {
        GuardianForm form = form(completedDays);
        if (form == GuardianForm.EGG || selected == null) return eggModel;
        GuardianSpeciesDefinition definition = species(selected);
        return form == GuardianForm.BABY ? definition.babyModel() : definition.adultModel();
    }
}
