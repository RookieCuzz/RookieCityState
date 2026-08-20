package com.cuzz.rookiecitystate.social;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Player-centred weekly quota ledger; city archival never removes entries. */
final class SocialVoteLedger {
    private final File file;
    private YamlConfiguration yaml;

    SocialVoteLedger(RookieCityState plugin) {
        file = new File(plugin.getDataFolder(), "data" + File.separator + "social" + File.separator + "voter-ledger.yml");
        yaml = file.isFile() ? YamlFiles.load(file) : new YamlConfiguration();
        if (!yaml.contains("schema_version")) {
            yaml.set("schema_version", 1);
            save();
        }
    }

    synchronized boolean reserve(UUID voter, UUID city, String week, int limit) {
        String path = path(week, voter, city);
        if (yaml.contains(path)) return false;
        if (votes(voter, week).size() >= limit) return false;
        yaml.set(path + ".state", "PENDING");
        yaml.set(path + ".operation_id", UUID.randomUUID().toString());
        yaml.set(path + ".updated_at", System.currentTimeMillis());
        save();
        return true;
    }

    synchronized void commit(UUID voter, UUID city, String week) {
        String path = path(week, voter, city);
        if (!yaml.contains(path)) throw new IllegalStateException("点赞额度预留不存在");
        yaml.set(path + ".state", "COMMITTED");
        yaml.set(path + ".updated_at", System.currentTimeMillis());
        save();
    }

    synchronized void rollback(UUID voter, UUID city, String week) {
        String path = path(week, voter, city);
        if (!"PENDING".equals(yaml.getString(path + ".state"))) return;
        yaml.set(path, null);
        save();
    }

    synchronized void migrateCommitted(UUID voter, UUID city, String week) {
        String path = path(week, voter, city);
        if ("COMMITTED".equals(yaml.getString(path + ".state"))) return;
        yaml.set(path + ".state", "COMMITTED");
        yaml.set(path + ".migration", true);
        yaml.set(path + ".updated_at", System.currentTimeMillis());
        save();
    }

    synchronized Set<UUID> votes(UUID voter, String week) {
        ConfigurationSection cities = yaml.getConfigurationSection("weeks." + week + ".voters." + voter + ".cities");
        if (cities == null) return Set.of();
        Set<UUID> result = new HashSet<>();
        for (String key : cities.getKeys(false)) {
            try {
                String state = cities.getString(key + ".state");
                if ("PENDING".equals(state) || "COMMITTED".equals(state)) result.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) { }
        }
        return Set.copyOf(result);
    }

    synchronized Set<UUID> voters(String week) {
        ConfigurationSection voters = yaml.getConfigurationSection("weeks." + week + ".voters");
        if (voters == null) return Set.of();
        Set<UUID> result = new HashSet<>();
        for (String key : voters.getKeys(false)) {
            try { result.add(UUID.fromString(key)); } catch (IllegalArgumentException ignored) { }
        }
        return Set.copyOf(result);
    }

    private String path(String week, UUID voter, UUID city) {
        return "weeks." + week + ".voters." + voter + ".cities." + city;
    }

    private void save() { YamlFiles.save(yaml, file); }
}
