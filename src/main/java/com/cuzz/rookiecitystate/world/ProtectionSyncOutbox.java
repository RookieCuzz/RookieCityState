package com.cuzz.rookiecitystate.world;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Durable desired-state record for RookieRegions membership synchronization. */
final class ProtectionSyncOutbox {
    private final File folder;

    ProtectionSyncOutbox(RookieCityState plugin) {
        this.folder = new File(plugin.getDataFolder(), "data" + File.separator + "operations"
                + File.separator + "protection-sync");
    }

    synchronized Entry stage(CityState cityState) {
        List<UUID> owners = List.of(cityState.getOwner().getUuid());
        List<UUID> members = cityState.getMembers().stream().map(CityStateMember::getUuid)
                .filter(id -> !owners.contains(id)).sorted().toList();
        String hash = hash(cityState.getUuid(), cityState.getWorldName(), owners, members);
        Entry previous = get(cityState.getUuid());
        if (previous != null && previous.hash().equals(hash)) return previous;
        long version = previous == null ? 1L : previous.version() + 1L;
        Entry entry = new Entry(cityState.getUuid(), cityState.getWorldName(), version, hash,
                owners, members, 0, 0L, null);
        save(entry);
        return entry;
    }

    synchronized Entry failure(Entry expected, Throwable error, long nextRetryAt) {
        Entry current = get(expected.cityId());
        if (current == null || !current.hash().equals(expected.hash())) return current;
        Entry failed = new Entry(current.cityId(), current.worldName(), current.version(), current.hash(),
                current.owners(), current.members(), current.attempts() + 1, nextRetryAt,
                error == null ? "unknown" : error.getClass().getSimpleName() + ": " + error.getMessage());
        save(failed);
        return failed;
    }

    synchronized boolean complete(Entry expected) {
        Entry current = get(expected.cityId());
        if (current == null) return true;
        if (!current.hash().equals(expected.hash())) return false;
        try {
            Files.deleteIfExists(file(expected.cityId()).toPath());
            return true;
        } catch (Exception error) {
            throw new IllegalStateException("无法删除已完成的成员同步 outbox", error);
        }
    }

    synchronized boolean has(UUID cityId) { return file(cityId).isFile(); }

    synchronized Entry get(UUID cityId) {
        File file = file(cityId);
        if (!file.isFile()) return null;
        try {
            YamlConfiguration yaml = YamlFiles.load(file);
            UUID storedId = UUID.fromString(require(yaml.getString("city_uuid"), "city_uuid"));
            if (!storedId.equals(cityId)) throw new IllegalArgumentException("city_uuid 与文件名不一致");
            String worldName = require(yaml.getString("world_name"), "world_name");
            long version = yaml.getLong("target.version");
            if (version < 1L) throw new IllegalArgumentException("target.version 无效");
            String hash = require(yaml.getString("target.hash"), "target.hash");
            List<UUID> owners = parseIds(yaml.getStringList("target.owners"), "target.owners");
            List<UUID> members = parseIds(yaml.getStringList("target.members"), "target.members");
            if (owners.isEmpty()) throw new IllegalArgumentException("target.owners 不能为空");
            String calculated = hash(storedId, worldName, owners, members);
            if (!calculated.equals(hash)) throw new IllegalArgumentException("target.hash 校验失败");
            return new Entry(storedId, worldName, version, hash, owners, members,
                    Math.max(0, yaml.getInt("retry.attempts")), yaml.getLong("retry.next_at"),
                    yaml.getString("retry.last_error"));
        } catch (RuntimeException error) {
            quarantine(file, error);
            return null;
        }
    }

    synchronized List<Entry> loadAll() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return List.of();
        java.util.Arrays.sort(files, Comparator.comparing(File::getName));
        List<Entry> result = new ArrayList<>();
        for (File candidate : files) {
            try {
                UUID cityId = UUID.fromString(candidate.getName().substring(0, candidate.getName().length() - 4));
                Entry entry = get(cityId);
                if (entry != null) result.add(entry);
            } catch (RuntimeException error) {
                quarantine(candidate, error);
            }
        }
        return List.copyOf(result);
    }

    private void save(Entry entry) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("type", "PROTECTION_SYNC");
        yaml.set("city_uuid", entry.cityId().toString());
        yaml.set("world_name", entry.worldName());
        yaml.set("target.version", entry.version());
        yaml.set("target.hash", entry.hash());
        yaml.set("target.owners", entry.owners().stream().map(UUID::toString).toList());
        yaml.set("target.members", entry.members().stream().map(UUID::toString).toList());
        yaml.set("retry.attempts", entry.attempts());
        yaml.set("retry.next_at", entry.nextRetryAt());
        yaml.set("retry.last_error", entry.lastError());
        yaml.set("updated_at", System.currentTimeMillis());
        YamlFiles.save(yaml, file(entry.cityId()));
    }

    private File file(UUID cityId) { return new File(folder, cityId + ".yml"); }

    private void quarantine(File file, RuntimeException error) {
        try {
            File quarantine = new File(folder.getParentFile().getParentFile(), "quarantine"
                    + File.separator + "protection-sync");
            Files.createDirectories(quarantine.toPath());
            File target = new File(quarantine, file.getName() + "." + System.currentTimeMillis() + ".corrupt");
            Files.move(file.toPath(), target.toPath());
            PluginLogger.warning("损坏的成员同步 outbox 已隔离: " + file.getName() + " (" + error.getMessage() + ")");
        } catch (Exception moveError) {
            throw new IllegalStateException("无法隔离损坏的成员同步 outbox: " + file, moveError);
        }
    }

    private static List<UUID> parseIds(List<String> values, String path) {
        List<UUID> result = new ArrayList<>();
        for (String value : values) {
            try { result.add(UUID.fromString(value)); }
            catch (RuntimeException error) { throw new IllegalArgumentException(path + " 包含无效 UUID", error); }
        }
        return result.stream().distinct().sorted().toList();
    }

    private static String hash(UUID cityId, String worldName, List<UUID> owners, List<UUID> members) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = cityId + "\n" + worldName + "\n"
                    + owners.stream().sorted().map(UUID::toString).reduce("", (a, b) -> a + b + "\n")
                    + "--\n" + members.stream().sorted().map(UUID::toString).reduce("", (a, b) -> a + b + "\n");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("无法计算成员同步哈希", error);
        }
    }

    private static String require(String value, String path) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(path + " 不能为空");
        return value;
    }

    record Entry(UUID cityId, String worldName, long version, String hash,
                 List<UUID> owners, List<UUID> members, int attempts,
                 long nextRetryAt, String lastError) { }
}
