package com.cuzz.rookiecitystate.wishtree;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WishTreeStore {
    private final RookieCityState plugin;
    private final Map<UUID, WishTreeState> cache = new ConcurrentHashMap<>();

    public WishTreeStore(RookieCityState plugin) { this.plugin = plugin; }

    public WishTreeState get(CityState cityState) {
        return cache.computeIfAbsent(cityState.getUuid(), id -> new WishTreeState(file(id), id,
                cityState.getYaml().getInt("world.template.revision", 1)));
    }

    public WishTreeState get(UUID cityStateId, int templateRevision) {
        return cache.computeIfAbsent(cityStateId, id -> new WishTreeState(file(id), id, templateRevision));
    }

    public void archive(CityState cityState) {
        WishTreeState state = cache.remove(cityState.getUuid());
        File source = state == null ? file(cityState.getUuid()) : state.file();
        if (!source.exists()) return;
        File folder = new File(plugin.getDataFolder(), "data/wish_trees/deleted");
        try {
            Files.createDirectories(folder.toPath());
            Files.move(source.toPath(), new File(folder, System.currentTimeMillis() + "-" + source.getName()).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            throw new IllegalStateException("无法归档许愿树数据", error);
        }
    }

    public void backup(WishTreeState state, String suffix) {
        File backup = new File(state.file().getParentFile(), state.file().getName() + ".bak." + suffix);
        YamlFiles.save(state.yaml(), backup);
    }

    private File file(UUID id) {
        return new File(plugin.getDataFolder(), "data/wish_trees/" + id + ".yml");
    }
}
