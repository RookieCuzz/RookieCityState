package com.cuzz.rookiecitystate.world;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.nio.file.Path;

public final class UnavailableCityWorldBackend implements CityWorldBackend {
    private final String reason;

    public UnavailableCityWorldBackend(String reason) {
        this.reason = reason;
    }

    @Override public String name() { return "UNAVAILABLE"; }
    @Override public boolean available() { return false; }
    @Override public PreparedCopy prepareCopy(WorldProvisionSpec spec) { throw new IllegalStateException(reason); }
    @Override public boolean exists(String worldName) { return Bukkit.getWorld(worldName) != null || worldFolder(worldName).toFile().isDirectory(); }
    @Override public World load(String worldName) { throw new IllegalStateException(reason); }
    @Override public boolean unload(String worldName, boolean save) { return false; }
    @Override public void configureManagedWorld(String worldName, World world) { throw new IllegalStateException(reason); }
    @Override public void forget(String worldName) { }
    @Override public Path worldFolder(String worldName) { return Bukkit.getWorldContainer().toPath().resolve(worldName).normalize(); }
}
