package com.cuzz.rookiecitystate.social;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.logger.PluginLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class CitySocialStore {
    private final RookieCityState plugin;
    private final File folder;
    private final Map<UUID, CitySocialState> states = new HashMap<>();
    private final Map<UUID, String> errors = new HashMap<>();

    CitySocialStore(RookieCityState plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "data" + File.separator + "social" + File.separator + "cities");
    }

    synchronized void loadAll() {
        states.clear(); errors.clear();
        loadErrorMarkers();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        for (File file : files) {
            UUID id = null;
            try {
                id = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                CityState city = plugin.getCityStateManager().getCityState(id);
                if (city == null) {
                    PluginLogger.warning("发现没有活动城邦归属的社交数据: " + file.getName());
                    continue;
                }
                states.put(id, CitySocialState.load(id, file));
                errors.remove(id);
                Files.deleteIfExists(errorFile(id).toPath());
            } catch (RuntimeException | IOException error) {
                if (id != null) errors.put(id, rootMessage(error));
                quarantine(file, error);
            }
        }
    }

    synchronized CitySocialState state(CityState city) {
        String error = errors.get(city.getUuid());
        if (error != null) throw new IllegalStateException("城邦社交数据不可用: " + error);
        return states.computeIfAbsent(city.getUuid(), id -> CitySocialState.empty(id, file(id)));
    }

    synchronized CitySocialState loaded(UUID cityId) { return states.get(cityId); }
    synchronized Collection<CitySocialState> loadedStates() { return java.util.List.copyOf(states.values()); }
    synchronized String error(UUID cityId) { return errors.get(cityId); }

    synchronized void archive(CityState city) {
        CitySocialState state = states.remove(city.getUuid());
        errors.remove(city.getUuid());
        try { Files.deleteIfExists(errorFile(city.getUuid()).toPath()); }
        catch (IOException error) { throw new IllegalStateException("无法清理城邦社交错误标记", error); }
        File source = state == null ? file(city.getUuid()) : state.file();
        if (!source.isFile()) return;
        File target = new File(plugin.getDataFolder(), "data" + File.separator + "social"
                + File.separator + "deleted" + File.separator + city.getUuid()
                + File.separator + System.currentTimeMillis() + ".yml");
        try {
            Files.createDirectories(target.getParentFile().toPath());
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) { throw new IllegalStateException("无法归档城邦社交数据", error); }
    }

    synchronized File backup(CityState city, String action) {
        CitySocialState state = state(city);
        File backup = new File(plugin.getDataFolder(), "data" + File.separator + "social"
                + File.separator + "admin_backups" + File.separator + city.getUuid()
                + File.separator + System.currentTimeMillis() + "-" + action + ".yml");
        try {
            Files.createDirectories(backup.getParentFile().toPath());
            Files.writeString(backup.toPath(), state.dump(), StandardCharsets.UTF_8);
            return backup;
        } catch (IOException error) { throw new IllegalStateException("无法备份城邦社交数据", error); }
    }

    private File file(UUID id) { return new File(folder, id + ".yml"); }

    private void quarantine(File file, Throwable failure) {
        try {
            File quarantine = new File(plugin.getDataFolder(), "data" + File.separator + "quarantine"
                    + File.separator + "social");
            Files.createDirectories(quarantine.toPath());
            File target = new File(quarantine, System.currentTimeMillis() + "-" + file.getName());
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(new File(quarantine, target.getName() + ".reason.txt").toPath(),
                    rootMessage(failure), StandardCharsets.UTF_8);
            UUID id = parseId(file);
            if (id != null) {
                File marker = errorFile(id);
                Files.createDirectories(marker.getParentFile().toPath());
                Files.writeString(marker.toPath(), rootMessage(failure), StandardCharsets.UTF_8);
            }
            PluginLogger.warning("已隔离损坏的城邦社交数据: " + file.getName());
        } catch (IOException error) {
            PluginLogger.error("无法隔离损坏的城邦社交数据 " + file.getName(), new IllegalStateException(error));
        }
    }

    private static String rootMessage(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private void loadErrorMarkers() {
        File errorFolder = new File(plugin.getDataFolder(), "data" + File.separator + "social" + File.separator + "errors");
        File[] files = errorFolder.listFiles((dir, name) -> name.endsWith(".error"));
        if (files == null) return;
        for (File file : files) {
            try {
                UUID id = UUID.fromString(file.getName().substring(0, file.getName().length() - 6));
                errors.put(id, Files.readString(file.toPath(), StandardCharsets.UTF_8));
            } catch (RuntimeException | IOException error) {
                PluginLogger.warning("忽略无效的社交错误标记: " + file.getName());
            }
        }
    }

    private File errorFile(UUID id) {
        return new File(plugin.getDataFolder(), "data" + File.separator + "social"
                + File.separator + "errors" + File.separator + id + ".error");
    }

    private UUID parseId(File file) {
        try { return UUID.fromString(file.getName().substring(0, file.getName().length() - 4)); }
        catch (RuntimeException ignored) { return null; }
    }
}
