package com.cuzz.rookiecitystate.guardian.shop;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

public final class GuardianShopRotationStore {
    private final File folder;

    public GuardianShopRotationStore(RookieCityState plugin) {
        this.folder = new File(plugin.getDataFolder(), "data" + File.separator + "guardian_shop"
                + File.separator + "rotations");
    }

    public synchronized GuardianShopRotation current(GuardianShopConfig config, long now) {
        String cycle = config.clock().week(now);
        File file = file(cycle);
        try {
            if (file.exists()) return load(file);
            long seed = java.util.concurrent.ThreadLocalRandom.current().nextLong();
            GuardianShopRotation generated = generate(cycle, seed, now, config, new Random(seed));
            save(generated, file);
            purge(config.retentionWeeks());
            return generated;
        } catch (RuntimeException error) {
            GuardianShopRotation fallback = newest();
            if (fallback == null) throw error;
            PluginLogger.error("本周灵兽贡献商店轮换生成失败，继续使用上一份快照。", error);
            return fallback;
        }
    }

    public synchronized GuardianShopRotation rotate(GuardianShopConfig config, long now) {
        String cycle = config.clock().week(now);
        File target = file(cycle);
        backup(target);
        long seed = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        GuardianShopRotation generated = generate(cycle, seed, now, config, new Random(seed));
        save(generated, target);
        purge(config.retentionWeeks());
        return generated;
    }

    static GuardianShopRotation generate(String cycle, long seed, long generatedAt,
                                         GuardianShopConfig config, RandomGenerator random) {
        List<GuardianShopProduct> available = new ArrayList<>(config.products().values());
        List<GuardianShopProduct> selected = new ArrayList<>();
        while (selected.size() < config.rotationSize()) {
            double total = available.stream().mapToDouble(GuardianShopProduct::weight).sum();
            if (!Double.isFinite(total) || total <= 0D) throw new IllegalStateException("商品权重总和非法");
            double roll = random.nextDouble(total);
            GuardianShopProduct chosen = available.get(available.size() - 1);
            for (GuardianShopProduct candidate : available) {
                roll -= candidate.weight();
                if (roll < 0D) { chosen = candidate; break; }
            }
            selected.add(chosen);
            available.remove(chosen);
        }
        return new GuardianShopRotation(cycle, seed, generatedAt, config.revision(), selected);
    }

    public synchronized GuardianShopRotation load(File file) {
        YamlConfiguration yaml = YamlFiles.load(file);
        String cycle = yaml.getString("cycle");
        if (cycle == null || cycle.isBlank()) throw new IllegalArgumentException("轮换 cycle 缺失: " + file.getName());
        ConfigurationSection products = yaml.getConfigurationSection("products");
        if (products == null) throw new IllegalArgumentException("轮换商品快照缺失: " + file.getName());
        List<GuardianShopProduct> snapshots = products.getKeys(false).stream()
                .sorted(Comparator.comparingInt(Integer::parseInt))
                .map(key -> GuardianShopProduct.loadSnapshot(products.getConfigurationSection(key))).toList();
        if (snapshots.isEmpty()) throw new IllegalArgumentException("轮换商品为空: " + file.getName());
        return new GuardianShopRotation(cycle, yaml.getLong("seed"), yaml.getLong("generated_at"),
                yaml.getInt("config_revision", 1), snapshots);
    }

    private void save(GuardianShopRotation rotation, File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema_version", 1);
        yaml.set("cycle", rotation.cycle());
        yaml.set("seed", rotation.seed());
        yaml.set("generated_at", rotation.generatedAt());
        yaml.set("config_revision", rotation.configRevision());
        for (int i = 0; i < rotation.products().size(); i++) {
            rotation.products().get(i).save(yaml.createSection("products." + i));
        }
        YamlFiles.save(yaml, file);
    }

    private GuardianShopRotation newest() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) return null;
        java.util.Arrays.sort(files, Comparator.comparing(File::getName).reversed());
        for (File file : files) try { return load(file); }
        catch (RuntimeException error) { PluginLogger.warning("忽略损坏的灵兽商店轮换: " + file.getName()); }
        return null;
    }

    private void backup(File target) {
        if (!target.exists()) return;
        try {
            File backup = new File(target.getParentFile(), target.getName() + ".bak." + System.currentTimeMillis());
            Files.copy(target.toPath(), backup.toPath());
        } catch (IOException error) {
            throw new IllegalStateException("无法备份本周轮换", error);
        }
    }

    private void purge(int retentionWeeks) {
        File[] files = folder.listFiles((dir, name) -> name.matches("\\d{4}-\\d{2}-\\d{2}\\.yml"));
        if (files == null || files.length <= retentionWeeks) return;
        java.util.Arrays.sort(files, Comparator.comparing(File::getName).reversed());
        for (int i = retentionWeeks; i < files.length; i++) {
            try { Files.deleteIfExists(files[i].toPath()); }
            catch (IOException error) { PluginLogger.warning("无法清理旧灵兽商店轮换: " + files[i].getName()); }
        }
    }

    private File file(String cycle) { return new File(folder, cycle + ".yml"); }
}
