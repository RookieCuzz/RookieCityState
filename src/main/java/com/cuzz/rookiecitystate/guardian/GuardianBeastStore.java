package com.cuzz.rookiecitystate.guardian;

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

public final class GuardianBeastStore {
    private final RookieCityState plugin;
    private final Map<UUID, GuardianBeastState> cache = new ConcurrentHashMap<>();

    public GuardianBeastStore(RookieCityState plugin) { this.plugin = plugin; }

    public GuardianBeastState get(CityState cityState, int modelRevision) {
        return get(cityState.getUuid(), modelRevision);
    }

    public GuardianBeastState get(UUID cityId, int modelRevision) {
        return cache.computeIfAbsent(cityId, id -> new GuardianBeastState(file(id), id, modelRevision));
    }

    public void backup(GuardianBeastState state, String reason) {
        File backup = new File(state.file().getParentFile(), state.file().getName() + ".bak." + reason);
        YamlFiles.save(state.yaml(), backup);
    }

    public void archive(CityState cityState) {
        GuardianBeastState state = cache.remove(cityState.getUuid());
        File source = state == null ? file(cityState.getUuid()) : state.file();
        if (!source.exists()) return;
        File folder = new File(plugin.getDataFolder(), "data/guardian_beasts/deleted");
        try {
            Files.createDirectories(folder.toPath());
            Files.move(source.toPath(), new File(folder, System.currentTimeMillis() + "-" + source.getName()).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            throw new IllegalStateException("无法归档公共灵兽数据", error);
        }
    }

    private File file(UUID id) {
        return new File(plugin.getDataFolder(), "data/guardian_beasts/" + id + ".yml");
    }
}
