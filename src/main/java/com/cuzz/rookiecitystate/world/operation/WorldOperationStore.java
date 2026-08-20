package com.cuzz.rookiecitystate.world.operation;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.nio.file.Files;

public final class WorldOperationStore {
    private final File folder;

    public WorldOperationStore(RookieCityState plugin) {
        folder = new File(plugin.getDataFolder(), "data" + File.separator + "operations");
    }

    public CityWorldOperation create(UUID id, UUID cityStateId, String worldName, WorldOperationKind kind) {
        File file = file(id);
        if (file.exists()) throw new IllegalStateException("Operation already exists: " + id);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", id.toString());
        yaml.set("city_uuid", cityStateId.toString());
        yaml.set("world_name", worldName);
        yaml.set("kind", kind.name());
        yaml.set("phase", "PREPARED");
        yaml.set("payment.state", PaymentState.NOT_CHARGED.name());
        yaml.set("created_at", System.currentTimeMillis());
        yaml.set("updated_at", System.currentTimeMillis());
        YamlFiles.save(yaml, file);
        return new CityWorldOperation(file, yaml);
    }

    public CityWorldOperation get(UUID id) {
        File file = file(id);
        return file.exists() ? new CityWorldOperation(file, YamlFiles.load(file)) : null;
    }

    public List<CityWorldOperation> loadAll() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return List.of();
        Arrays.sort(files, Comparator.comparing(File::getName));
        List<CityWorldOperation> result = new ArrayList<>();
        for (File file : files) {
            try {
                CityWorldOperation operation = new CityWorldOperation(file, YamlFiles.load(file));
                operation.id();
                operation.cityStateId();
                operation.kind();
                operation.paymentState();
                result.add(operation);
            } catch (RuntimeException exception) {
                quarantine(file, exception);
            }
        }
        return List.copyOf(result);
    }

    public void remove(CityWorldOperation operation) {
        if (!operation.file().delete() && operation.file().exists()) {
            throw new IllegalStateException("Could not remove operation " + operation.id());
        }
    }

    private File file(UUID id) { return new File(folder, id + ".yml"); }

    private void quarantine(File file, RuntimeException error) {
        try {
            File quarantine = new File(folder.getParentFile(), "quarantine" + File.separator + "operations");
            Files.createDirectories(quarantine.toPath());
            File target = new File(quarantine, file.getName() + "." + System.currentTimeMillis() + ".corrupt");
            Files.move(file.toPath(), target.toPath());
            PluginLogger.warning("损坏的世界操作已隔离: " + file.getName() + " (" + error.getMessage() + ")");
        } catch (Exception moveError) {
            throw new IllegalStateException("无法隔离损坏的世界操作: " + file.getAbsolutePath(), moveError);
        }
    }
}
