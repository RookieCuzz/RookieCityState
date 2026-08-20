package com.cuzz.rookiecitystate.social;

import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class CitySocialState {
    private final UUID cityId;
    private final File file;
    private final YamlConfiguration yaml;

    private CitySocialState(UUID cityId, File file, YamlConfiguration yaml) {
        this.cityId = cityId;
        this.file = file;
        this.yaml = yaml;
    }

    static CitySocialState empty(UUID cityId, File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema_version", 1);
        yaml.set("city_uuid", cityId.toString());
        yaml.set("status", "READY");
        yaml.set("total_likes", 0L);
        return new CitySocialState(cityId, file, yaml);
    }

    static CitySocialState load(UUID expected, File file) {
        YamlConfiguration yaml = YamlFiles.load(file);
        int schema = yaml.getInt("schema_version", 0);
        if (schema != 1) throw new IllegalArgumentException("不支持的社交数据版本: " + schema);
        UUID actual = UUID.fromString(required(yaml, "city_uuid"));
        if (!actual.equals(expected)) throw new IllegalArgumentException("社交文件 UUID 与文件名不一致");
        String status = yaml.getString("status", "READY");
        if (!status.equals("READY")) throw new IllegalArgumentException("不支持的社交状态: " + status);
        long total = yaml.getLong("total_likes", -1L);
        if (total < 0L) throw new IllegalArgumentException("total_likes 不能为负数");
        validateVisits(yaml.getConfigurationSection("visits"));
        int retainedLikes = validateWeeklyEntries(yaml.getConfigurationSection("likes"), "likes");
        validateWeeklyEntries(yaml.getConfigurationSection("qualifications"), "qualifications");
        if (total < retainedLikes) throw new IllegalArgumentException("total_likes 小于保留的点赞明细数");
        return new CitySocialState(expected, file, yaml);
    }

    synchronized long totalLikes() { return yaml.getLong("total_likes", 0L); }

    synchronized boolean isQualified(UUID playerId, String week) {
        return yaml.contains("qualifications." + week + "." + playerId);
    }

    synchronized boolean hasLiked(UUID playerId, String week) {
        return yaml.contains("likes." + week + "." + playerId);
    }

    synchronized boolean qualify(UUID playerId, String day, String week, long now) {
        String qualification = "qualifications." + week + "." + playerId;
        String visit = "visits." + day + "." + playerId;
        boolean changed = !yaml.contains(qualification) || !yaml.contains(visit);
        if (!changed) return false;
        String before = yaml.saveToString();
        yaml.set(qualification + ".at", now);
        yaml.set(qualification + ".day", day);
        yaml.set(visit, now);
        saveOrRestore(before);
        return true;
    }

    synchronized boolean like(UUID playerId, String day, String week, long now) {
        String path = "likes." + week + "." + playerId;
        if (yaml.contains(path)) return false;
        String before = yaml.saveToString();
        yaml.set(path + ".at", now);
        yaml.set(path + ".day", day);
        yaml.set("total_likes", Math.addExact(totalLikes(), 1L));
        saveOrRestore(before);
        return true;
    }

    synchronized boolean revoke(UUID playerId, String week) {
        String path = "likes." + week + "." + playerId;
        if (!yaml.contains(path)) return false;
        String before = yaml.saveToString();
        yaml.set(path, null);
        yaml.set("total_likes", Math.max(0L, totalLikes() - 1L));
        saveOrRestore(before);
        return true;
    }

    synchronized Metrics metrics(Set<String> recentDays) {
        Set<UUID> visitors = new HashSet<>();
        ConfigurationSection visits = yaml.getConfigurationSection("visits");
        if (visits != null) {
            for (String day : recentDays) {
                ConfigurationSection bucket = visits.getConfigurationSection(day);
                if (bucket == null) continue;
                for (String raw : bucket.getKeys(false)) visitors.add(UUID.fromString(raw));
            }
        }
        int likes = 0;
        ConfigurationSection weeks = yaml.getConfigurationSection("likes");
        if (weeks != null) {
            for (String week : weeks.getKeys(false)) {
                ConfigurationSection voters = weeks.getConfigurationSection(week);
                if (voters == null) continue;
                for (String voter : voters.getKeys(false)) {
                    String day = voters.getString(voter + ".day");
                    if (day != null && recentDays.contains(day)) likes++;
                }
            }
        }
        return new Metrics(visitors.size(), likes);
    }

    synchronized Set<UUID> voters(String week) {
        ConfigurationSection section = yaml.getConfigurationSection("likes." + week);
        if (section == null) return Set.of();
        Set<UUID> result = new HashSet<>();
        for (String raw : section.getKeys(false)) result.add(UUID.fromString(raw));
        return Set.copyOf(result);
    }

    synchronized boolean prune(String oldestRetainedDay) {
        String before = yaml.saveToString();
        boolean changed = false;
        ConfigurationSection visits = yaml.getConfigurationSection("visits");
        if (visits != null) {
            for (String day : Set.copyOf(visits.getKeys(false))) {
                if (validDate(day).isBefore(LocalDate.parse(oldestRetainedDay))) {
                    visits.set(day, null); changed = true;
                }
            }
        }
        changed |= pruneDatedEntries("qualifications", oldestRetainedDay);
        changed |= pruneDatedEntries("likes", oldestRetainedDay);
        if (changed) saveOrRestore(before);
        return changed;
    }

    synchronized void resetRecent() {
        String before = yaml.saveToString();
        yaml.set("visits", null);
        yaml.set("qualifications", null);
        yaml.set("likes", null);
        saveOrRestore(before);
    }

    synchronized void resetAll() {
        String before = yaml.saveToString();
        yaml.set("visits", null);
        yaml.set("qualifications", null);
        yaml.set("likes", null);
        yaml.set("total_likes", 0L);
        saveOrRestore(before);
    }

    synchronized String dump() { return yaml.saveToString(); }
    UUID cityId() { return cityId; }
    File file() { return file; }

    private boolean pruneDatedEntries(String rootPath, String oldestRetainedDay) {
        ConfigurationSection groups = yaml.getConfigurationSection(rootPath);
        if (groups == null) return false;
        LocalDate oldest = LocalDate.parse(oldestRetainedDay);
        boolean changed = false;
        for (String group : Set.copyOf(groups.getKeys(false))) {
            ConfigurationSection entries = groups.getConfigurationSection(group);
            if (entries == null) continue;
            for (String id : Set.copyOf(entries.getKeys(false))) {
                String day = entries.getString(id + ".day");
                if (day == null || validDate(day).isBefore(oldest)) {
                    entries.set(id, null); changed = true;
                }
            }
            if (entries.getKeys(false).isEmpty()) groups.set(group, null);
        }
        return changed;
    }

    private void saveOrRestore(String before) {
        try { YamlFiles.save(yaml, file); }
        catch (RuntimeException error) {
            try { yaml.loadFromString(before); }
            catch (InvalidConfigurationException restoreError) { error.addSuppressed(restoreError); }
            throw error;
        }
    }

    private static void validateVisits(ConfigurationSection root) {
        if (root == null) return;
        for (String day : root.getKeys(false)) {
            validDate(day);
            ConfigurationSection visitors = root.getConfigurationSection(day);
            if (visitors == null) throw new IllegalArgumentException("visits." + day + " 必须是对象");
            for (String id : visitors.getKeys(false)) {
                UUID.fromString(id);
                if (!isWholeNumber(visitors, id) || visitors.getLong(id) < 0L) {
                    throw new IllegalArgumentException("无效参观时间: visits." + day + "." + id);
                }
            }
        }
    }

    private static int validateWeeklyEntries(ConfigurationSection root, String rootName) {
        if (root == null) return 0;
        int count = 0;
        for (String week : root.getKeys(false)) {
            if (validDate(week).getDayOfWeek() != DayOfWeek.MONDAY) {
                throw new IllegalArgumentException("无效周周期: " + week);
            }
            ConfigurationSection entries = root.getConfigurationSection(week);
            if (entries == null) throw new IllegalArgumentException(rootName + "." + week + " 必须是对象");
            for (String id : entries.getKeys(false)) {
                UUID.fromString(id);
                ConfigurationSection entry = entries.getConfigurationSection(id);
                if (entry == null || !isWholeNumber(entry, "at") || entry.getLong("at") < 0L) {
                    throw new IllegalArgumentException("无效社交明细: " + rootName + "." + week + "." + id);
                }
                validDate(required(entry, "day"));
                count = Math.addExact(count, 1);
            }
        }
        return count;
    }

    private static LocalDate validDate(String day) {
        try { return LocalDate.parse(day); }
        catch (RuntimeException error) { throw new IllegalArgumentException("无效社交日期: " + day, error); }
    }

    private static boolean isWholeNumber(ConfigurationSection section, String path) {
        return section.isInt(path) || section.isLong(path);
    }

    private static String required(YamlConfiguration yaml, String path) {
        String value = yaml.getString(path);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(path + " 缺失");
        return value;
    }

    private static String required(ConfigurationSection section, String path) {
        String value = section.getString(path);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(path + " 缺失");
        return value;
    }

    record Metrics(int visitors, int likes) { }
}
