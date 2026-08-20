package com.cuzz.rookiecitystate.world.myworlds;

import com.cuzz.rookiecitystate.world.CityWorldBackend;
import com.cuzz.rookiecitystate.world.WorldProvisionSpec;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * All MyWorlds implementation details are intentionally isolated here. MyWorlds does not publish
 * a versioned service API, so this adapter binds only to the small public WorldConfig surface used
 * by RookieCityState.
 */
public final class MyWorldsCityWorldBackend implements CityWorldBackend {
    private final Plugin myWorlds;
    private final Class<?> worldConfigClass;
    private final Class<? extends Enum<?>> startupModeClass;
    private final Method getConfig;
    private final Method copyTo;
    private final Method loadWorld;
    private final Method unloadWorld;
    private final Method updateAll;
    private final Method setStartupLoadMode;
    private final Method removeConfig;
    private final Method saveAllLater;

    @SuppressWarnings("unchecked")
    public MyWorldsCityWorldBackend() throws ReflectiveOperationException {
        this.myWorlds = Bukkit.getPluginManager().getPlugin("My_Worlds");
        if (myWorlds == null || !myWorlds.isEnabled()) {
            throw new IllegalStateException("My_Worlds is not enabled");
        }
        java.io.File myWorldsConfig = new java.io.File(myWorlds.getDataFolder(), "config.yml");
        if (myWorldsConfig.isFile()
                && YamlConfiguration.loadConfiguration(myWorldsConfig).getBoolean("useWorldInventories", false)) {
            throw new IllegalStateException("MyWorlds useWorldInventories must be false so city worlds share inventory/XP");
        }
        worldConfigClass = Class.forName("com.bergerkiller.bukkit.mw.WorldConfig", true,
                myWorlds.getClass().getClassLoader());
        startupModeClass = (Class<? extends Enum<?>>) Class.forName(
                "com.bergerkiller.bukkit.mw.WorldStartupLoadMode", true, myWorlds.getClass().getClassLoader());
        getConfig = worldConfigClass.getMethod("get", String.class);
        copyTo = worldConfigClass.getMethod("copyTo", worldConfigClass);
        loadWorld = worldConfigClass.getMethod("loadWorld");
        unloadWorld = worldConfigClass.getMethod("unloadWorld");
        updateAll = worldConfigClass.getMethod("updateAll", World.class);
        setStartupLoadMode = worldConfigClass.getMethod("setStartupLoadMode", startupModeClass);
        removeConfig = worldConfigClass.getMethod("remove", String.class);
        saveAllLater = worldConfigClass.getMethod("saveAllLater");
    }

    @Override
    public String name() {
        return "MYWORLDS";
    }

    @Override
    public boolean available() {
        return myWorlds.isEnabled();
    }

    @Override
    public PreparedCopy prepareCopy(WorldProvisionSpec spec) throws Exception {
        requirePrimaryThread();
        if (!worldFolder(spec.templateWorld()).toFile().isDirectory()) {
            throw new IllegalStateException("Template world does not exist: " + spec.templateWorld());
        }
        if (exists(spec.targetWorld())) {
            throw new IllegalStateException("Target world already exists: " + spec.targetWorld());
        }
        Object source = invoke(getConfig, null, spec.templateWorld());
        Object target = invoke(getConfig, null, spec.targetWorld());
        return () -> {
            Object copied = invoke(copyTo, source, target);
            if (!(copied instanceof Boolean result) || !result) {
                throw new IllegalStateException("MyWorlds failed to copy " + spec.templateWorld()
                        + " to " + spec.targetWorld());
            }
        };
    }

    @Override
    public boolean exists(String worldName) {
        return Bukkit.getWorld(worldName) != null || worldFolder(worldName).toFile().isDirectory();
    }

    @Override
    public World load(String worldName) throws Exception {
        requirePrimaryThread();
        Object config = invoke(getConfig, null, worldName);
        Object value = invoke(loadWorld, config);
        if (!(value instanceof World world)) {
            throw new IllegalStateException("MyWorlds could not load world: " + worldName);
        }
        return world;
    }

    @Override
    public boolean unload(String worldName, boolean save) throws Exception {
        requirePrimaryThread();
        World world = Bukkit.getWorld(worldName);
        if (world == null) return true;
        if (!world.getPlayers().isEmpty()) return false;
        if (save) world.save();
        Object config = invoke(getConfig, null, worldName);
        return Boolean.TRUE.equals(invoke(unloadWorld, config));
    }

    @Override
    public void configureManagedWorld(String worldName, World world) throws Exception {
        requirePrimaryThread();
        Object config = invoke(getConfig, null, worldName);
        setBoolean(config, "keepSpawnInMemory", false);
        setBoolean(config, "pvp", false);
        setBoolean(config, "autosave", true);
        setBoolean(config, "reloadWhenEmpty", false);
        setBoolean(config, "clearInventory", false);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Enum<?> ignore = Enum.valueOf((Class) startupModeClass, "IGNORE");
        invoke(setStartupLoadMode, config, ignore);
        world.setPVP(false);
        world.setKeepSpawnInMemory(false);
        world.setAutoSave(true);
        invoke(updateAll, config, world);
        invoke(saveAllLater, null);
    }

    @Override
    public void forget(String worldName) throws Exception {
        requirePrimaryThread();
        invoke(removeConfig, null, worldName);
        invoke(saveAllLater, null);
    }

    @Override
    public Path worldFolder(String worldName) {
        Path root = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path resolved = root.resolve(worldName).normalize();
        if (!resolved.getParent().equals(root)) {
            throw new IllegalArgumentException("Invalid managed world name: " + worldName);
        }
        return resolved;
    }

    private void setBoolean(Object target, String name, boolean value) throws ReflectiveOperationException {
        Field field = worldConfigClass.getField(name);
        field.setBoolean(target, value);
    }

    private Object invoke(Method method, Object target, Object... args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) throw checked;
            if (cause instanceof Error error) throw error;
            throw exception;
        }
    }

    private void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("MyWorlds configuration/load operations must run on the server thread");
        }
    }
}
