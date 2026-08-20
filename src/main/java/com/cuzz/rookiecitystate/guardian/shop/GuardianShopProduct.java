package com.cuzz.rookiecitystate.guardian.shop;

import com.cuzz.rookiecitystate.guardian.GuardianAnimationStep;
import com.cuzz.rookiecitystate.guardian.GuardianForm;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record GuardianShopProduct(
        String id,
        String displayName,
        GuardianShopProductKind kind,
        long price,
        double weight,
        int weeklyLimit,
        int minimumGuardianLevel,
        Material icon,
        Particle particle,
        String text,
        Map<GuardianForm, List<GuardianAnimationStep>> animations,
        Material rewardMaterial,
        int rewardAmount
) {
    public GuardianShopProduct {
        animations = immutableAnimations(animations);
    }

    public boolean permanent() { return kind.permanent(); }

    public GuardianCosmeticSlot slot() {
        return permanent() ? GuardianCosmeticSlot.valueOf(kind.name()) : null;
    }

    public List<GuardianAnimationStep> animation(GuardianForm form) {
        return animations.getOrDefault(form, List.of());
    }

    public void save(ConfigurationSection root) {
        root.set("id", id);
        root.set("display_name", displayName);
        root.set("kind", kind.name());
        root.set("price", price);
        root.set("weight", weight);
        root.set("weekly_limit", weeklyLimit);
        root.set("minimum_guardian_level", minimumGuardianLevel);
        root.set("icon", icon.name());
        root.set("particle", particle == null ? null : particle.name());
        root.set("text", text);
        root.set("reward.material", rewardMaterial == null ? null : rewardMaterial.name());
        root.set("reward.amount", rewardAmount);
        root.set("animations", null);
        for (Map.Entry<GuardianForm, List<GuardianAnimationStep>> entry : animations.entrySet()) {
            String base = "animations." + entry.getKey().name().toLowerCase(java.util.Locale.ROOT);
            int index = 0;
            for (GuardianAnimationStep step : entry.getValue()) {
                String path = base + "." + index++;
                root.set(path + ".name", step.animation());
                root.set(path + ".duration_ticks", step.durationTicks());
                root.set(path + ".speed", step.speed());
                root.set(path + ".force", step.force());
            }
        }
    }

    public static GuardianShopProduct loadSnapshot(ConfigurationSection root) {
        if (root == null) throw new IllegalArgumentException("商品快照缺失");
        String id = required(root, "id");
        return GuardianShopConfig.parseProduct(id, root, true);
    }

    public YamlConfiguration snapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        save(yaml);
        return yaml;
    }

    private static Map<GuardianForm, List<GuardianAnimationStep>> immutableAnimations(
            Map<GuardianForm, List<GuardianAnimationStep>> source) {
        Map<GuardianForm, List<GuardianAnimationStep>> result = new EnumMap<>(GuardianForm.class);
        if (source != null) source.forEach((form, steps) -> result.put(form, List.copyOf(steps)));
        return Map.copyOf(result);
    }

    static Map<GuardianForm, List<GuardianAnimationStep>> loadAnimations(ConfigurationSection root) {
        Map<GuardianForm, List<GuardianAnimationStep>> result = new EnumMap<>(GuardianForm.class);
        ConfigurationSection animations = root.getConfigurationSection("animations");
        if (animations == null) return result;
        for (GuardianForm form : GuardianForm.values()) {
            ConfigurationSection sequence = animations.getConfigurationSection(form.name().toLowerCase(java.util.Locale.ROOT));
            if (sequence == null) continue;
            List<GuardianAnimationStep> steps = new ArrayList<>();
            sequence.getKeys(false).stream().sorted(java.util.Comparator.comparingInt(Integer::parseInt)).forEach(key -> {
                ConfigurationSection step = sequence.getConfigurationSection(key);
                if (step == null) throw new IllegalArgumentException("动画步骤无效: " + key);
                steps.add(new GuardianAnimationStep(required(step, "name"), step.getLong("duration_ticks"),
                        step.getDouble("speed", 1D), step.getBoolean("force", true)));
            });
            if (!steps.isEmpty()) result.put(form, List.copyOf(steps));
        }
        return result;
    }

    static String required(ConfigurationSection section, String path) {
        String value = section.getString(path);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(path + " 不能为空");
        return value.trim();
    }
}
