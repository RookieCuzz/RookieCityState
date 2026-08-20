package com.cuzz.rookiecitystate.world;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.nio.file.Path;

public interface CityWorldBackend {
    interface PreparedCopy {
        void copy() throws Exception;
    }

    String name();

    boolean available();

    PreparedCopy prepareCopy(WorldProvisionSpec spec) throws Exception;

    boolean exists(String worldName);

    World load(String worldName) throws Exception;

    boolean unload(String worldName, boolean save) throws Exception;

    void configureManagedWorld(String worldName, World world) throws Exception;

    default void save(World world) throws Exception {
        world.save();
    }

    void forget(String worldName) throws Exception;

    Path worldFolder(String worldName);

    default Path worldRoot() {
        return Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
    }
}
