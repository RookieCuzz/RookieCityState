package com.cuzz.rookiecitystate.internal.io;

import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
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
        Path temporary = null;
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            Path target = file.toPath();
            Path directory = target.toAbsolutePath().getParent();
            if (directory == null) {
                throw new IOException("YAML target has no parent directory: " + target);
            }
            temporary = Files.createTempFile(directory, file.getName() + ".", ".tmp");
            byte[] bytes = yaml.saveToString().getBytes(charset);
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // Reject our own incomplete output before replacing the last known-good file.
            load(temporary.toFile(), charset);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法保存 YAML: " + file.getAbsolutePath(), exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static void recoverAtomicWrites(Path root) {
        if (root == null || !Files.isDirectory(root)) return;
        try (var stream = Files.walk(root)) {
            List<Path> temporaryFiles = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".tmp")).toList();
            for (Path temporary : temporaryFiles) {
                String name = temporary.getFileName().toString();
                String withoutSuffix = name.substring(0, name.length() - ".tmp".length());
                int randomSeparator = withoutSuffix.lastIndexOf('.');
                if (randomSeparator <= 0) continue;
                String targetName = withoutSuffix.substring(0, randomSeparator);
                if (!targetName.endsWith(".yml")) continue;
                Path target = temporary.resolveSibling(targetName);
                if (Files.exists(target)) {
                    Files.deleteIfExists(temporary);
                    continue;
                }
                load(temporary.toFile(), StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, target);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法恢复未完成的 YAML 原子写入: " + root, exception);
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
