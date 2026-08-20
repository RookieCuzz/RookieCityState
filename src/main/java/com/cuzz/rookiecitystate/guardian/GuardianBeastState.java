package com.cuzz.rookiecitystate.guardian;

import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class GuardianBeastState {
    private static final int SCHEMA_VERSION = 2;
    private final File file;
    private final UUID cityId;
    private YamlConfiguration yaml;

    GuardianBeastState(File file, UUID cityId, int modelRevision) {
        this.file = file;
        this.cityId = cityId;
        if (file.exists()) {
            yaml = YamlFiles.load(file);
            if (yaml.getInt("schema_version", 0) > SCHEMA_VERSION) {
                throw new IllegalStateException("不支持未来版本的灵兽数据: " + file);
            }
            if (yaml.getInt("schema_version", 0) < SCHEMA_VERSION) {
                try {
                    java.nio.file.Files.copy(file.toPath(), file.toPath().resolveSibling(
                            file.getName() + ".schema1.bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.io.IOException error) {
                    throw new IllegalStateException("无法备份旧版灵兽数据", error);
                }
                yaml.set("schema_version", SCHEMA_VERSION);
                save();
            }
        } else {
            yaml = new YamlConfiguration();
            yaml.set("schema_version", SCHEMA_VERSION);
            yaml.set("city_uuid", cityId.toString());
            yaml.set("model_revision", modelRevision);
            yaml.set("completed_days", 0);
            yaml.set("visual.state", "PENDING");
            save();
        }
        String storedCity = yaml.getString("city_uuid");
        if (!cityId.toString().equals(storedCity)) throw new IllegalStateException("灵兽数据 UUID 不匹配: " + file);
    }

    public synchronized GuardianSpecies species() { return GuardianSpecies.parse(yaml.getString("species")); }
    public synchronized UUID selectedBy() {
        String value = yaml.getString("selected_by");
        return value == null ? null : UUID.fromString(value);
    }
    public synchronized int completedDays() { return Math.max(0, yaml.getInt("completed_days", 0)); }
    public synchronized String day() { return yaml.getString("daily.cycle", ""); }
    public synchronized int fullness() { return Math.max(0, yaml.getInt("daily.fullness", 0)); }
    public synchronized int target() { return Math.max(1, yaml.getInt("daily.target", 20)); }
    public synchronized boolean completedToday() { return yaml.getBoolean("daily.completed", false); }
    public synchronized int modelRevision() { return yaml.getInt("model_revision", 1); }
    public synchronized String visualError() { return yaml.getString("visual.last_error"); }

    public synchronized void ensureDay(String cycle, GuardianBeastConfig config, RandomGenerator random) {
        if (cycle.equals(day()) && yaml.isConfigurationSection("daily.favorites")) return;
        List<GuardianFood> candidates = new ArrayList<>(config.foods().values());
        for (int i = candidates.size() - 1; i > 0; i--) {
            int selected = random.nextInt(i + 1);
            Collections.swap(candidates, i, selected);
        }
        yaml.set("daily", null);
        yaml.set("daily.cycle", cycle);
        yaml.set("daily.target", config.target());
        yaml.set("daily.favorite_multiplier", config.favoriteMultiplier());
        yaml.set("daily.fullness", 0);
        yaml.set("daily.completed", false);
        for (GuardianFood food : candidates.subList(0, config.favoriteCount())) {
            String path = "daily.favorites." + food.material().name();
            yaml.set(path + ".material", food.material().name());
            yaml.set(path + ".nourishment", food.nourishment());
            yaml.set(path + ".contribution", food.contribution());
        }
        save();
    }

    public synchronized List<Material> favorites() {
        ConfigurationSection section = yaml.getConfigurationSection("daily.favorites");
        if (section == null) return List.of();
        List<Material> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(section.getString(key + ".material", key));
            if (material != null) result.add(material);
        }
        return List.copyOf(result);
    }

    public synchronized GuardianFood snapshottedFavorite(Material material) {
        if (material == null) return null;
        ConfigurationSection snapshot = yaml.getConfigurationSection("daily.favorites." + material.name());
        if (snapshot == null) return null;
        int nourishment = snapshot.getInt("nourishment", 0);
        int contribution = snapshot.getInt("contribution", 0);
        if (nourishment <= 0 || contribution < 0) return null;
        return new GuardianFood(material, nourishment, contribution, true);
    }

    public synchronized Map<UUID, Integer> dailyContributions() {
        ConfigurationSection section = yaml.getConfigurationSection("daily.participants");
        if (section == null) return Map.of();
        Map<UUID, Integer> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            try { result.put(UUID.fromString(key), Math.max(0, section.getInt(key + ".contribution"))); }
            catch (IllegalArgumentException ignored) { }
        }
        return Map.copyOf(result);
    }

    public synchronized void select(GuardianSpecies species, UUID playerId, long now) {
        if (species() != null) throw new IllegalStateException("灵兽种类已经选定");
        yaml.set("species", species.name());
        yaml.set("selected_by", playerId.toString());
        yaml.set("selected_at", now);
    }

    synchronized GuardianFeedMutation applyFeed(UUID playerId, GuardianFood configured,
                                                 int favoriteMultiplier, long now) {
        boolean favorite = favorites().contains(configured.material());
        int nourishment = configured.nourishment();
        int contribution = configured.contribution();
        if (favorite) {
            ConfigurationSection snapshot = yaml.getConfigurationSection("daily.favorites." + configured.material().name());
            if (snapshot != null) {
                nourishment = snapshot.getInt("nourishment", nourishment);
                contribution = snapshot.getInt("contribution", contribution);
            }
            int snapshottedMultiplier = yaml.getInt("daily.favorite_multiplier", favoriteMultiplier);
            nourishment = Math.multiplyExact(nourishment, snapshottedMultiplier);
            contribution = Math.multiplyExact(contribution, snapshottedMultiplier);
        }

        boolean newlyCompleted = false;
        if (!completedToday()) {
            int after = Math.min(target(), Math.addExact(fullness(), nourishment));
            yaml.set("daily.fullness", after);
            if (after >= target()) {
                yaml.set("daily.completed", true);
                yaml.set("daily.completed_at", now);
                yaml.set("completed_days", Math.addExact(completedDays(), 1));
                newlyCompleted = true;
            }
        }
        String participant = "daily.participants." + playerId;
        yaml.set(participant + ".feeds", Math.addExact(yaml.getInt(participant + ".feeds", 0), 1));
        yaml.set(participant + ".nourishment",
                Math.addExact(yaml.getInt(participant + ".nourishment", 0), nourishment));
        yaml.set(participant + ".contribution",
                Math.addExact(yaml.getInt(participant + ".contribution", 0), contribution));
        return new GuardianFeedMutation(nourishment, contribution, favorite, newlyCompleted);
    }

    public synchronized void resetDaily(String cycle, GuardianBeastConfig config, RandomGenerator random) {
        yaml.set("daily", null);
        ensureDay(cycle, config, random);
    }

    public synchronized void resetSpecies() {
        yaml.set("species", null);
        yaml.set("selected_by", null);
        yaml.set("selected_at", null);
        yaml.set("completed_days", 0);
        yaml.set("daily", null);
        yaml.set("visual.state", "PENDING");
        yaml.set("visual.last_error", null);
    }

    public synchronized void setCompletedDays(int days) {
        if (days < 0 || days > 53) throw new IllegalArgumentException("完成日必须为 0-53");
        yaml.set("completed_days", days);
        yaml.set("visual.state", "PENDING");
    }

    public synchronized void visualReady(String modelId) {
        yaml.set("visual.state", "READY");
        yaml.set("visual.model", modelId);
        yaml.set("visual.last_error", null);
        save();
    }

    public synchronized void visualFailed(Throwable error) {
        yaml.set("visual.state", "ERROR");
        yaml.set("visual.last_error", error == null ? "未知视觉错误" : String.valueOf(error.getMessage()));
        save();
    }

    public synchronized String snapshot() { return yaml.saveToString(); }

    public synchronized void restore(String snapshot) {
        YamlConfiguration restored = new YamlConfiguration();
        try { restored.loadFromString(snapshot); }
        catch (InvalidConfigurationException error) { throw new IllegalStateException("无法恢复灵兽事务快照", error); }
        yaml = restored;
    }

    public synchronized void save() { YamlFiles.save(yaml, file); }
    public synchronized YamlConfiguration yaml() { return yaml; }
    public File file() { return file; }
    public UUID cityId() { return cityId; }
}
