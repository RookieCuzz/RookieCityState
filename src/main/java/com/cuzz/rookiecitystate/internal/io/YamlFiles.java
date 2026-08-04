package com.cuzz.rookiecitystate.internal.io;

import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class YamlFiles {
    private YamlFiles() {
    }

    public static YamlConfiguration load(File file) {
        return load(file, StandardCharsets.UTF_8);
    }

    public static YamlConfiguration load(File file, Charset charset) {
        if (!file.exists()) {
            return new YamlConfiguration();
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), charset)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(reader);
            return yaml;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("无法读取 YAML: " + file.getAbsolutePath(), exception);
        }
    }

    public static void save(YamlConfiguration yaml, File file) {
        save(yaml, file, StandardCharsets.UTF_8);
    }

    public static void save(YamlConfiguration yaml, File file, Charset charset) {
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            try (Writer writer = Files.newBufferedWriter(file.toPath(), charset)) {
                writer.write(yaml.saveToString());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法保存 YAML: " + file.getAbsolutePath(), exception);
        }
    }

    public static Set<String> completeMissing(YamlConfiguration target, YamlConfiguration defaults) {
        Set<String> changes = new LinkedHashSet<>();
        defaults.getKeys(true).stream()
                .sorted(Comparator.comparingInt(path -> path.split("\\.").length))
                .forEach(path -> {
                    if (target.contains(path)) {
                        return;
                    }
                    if (defaults.isConfigurationSection(path)) {
                        target.createSection(path);
                    } else {
                        target.set(path, defaults.get(path));
                    }
                    changes.add(path);
                });
        return changes;
    }

    public static void writeLocation(org.bukkit.configuration.ConfigurationSection section, Location location) {
        section.set("world", location.getWorld().getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
    }
}
