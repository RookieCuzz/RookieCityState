package com.cuzz.rookiecitystate.world;

import com.cuzz.rookiecitystate.citystate.CityState;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class NoopCityWorldProtectionService implements CityWorldProtectionService {
    @Override public CompletionStage<Void> ensureTemplateCore(World template, String coreRegionId,
                                                              int minX, int minY, int minZ,
                                                              int maxX, int maxY, int maxZ) {
        return CompletableFuture.completedFuture(null);
    }
    @Override public void captureTemplate(World template, String coreRegionId) { }
    @Override public CompletionStage<Void> installTemplateSnapshot(World snapshot) {
        return CompletableFuture.completedFuture(null);
    }
    @Override public boolean hasTemplateSnapshot() { return false; }
    @Override public CompletionStage<Void> apply(CityState cityState, World world, int borderSize) {
        return CompletableFuture.completedFuture(null);
    }
    @Override public CompletionStage<Void> synchronizeMembers(CityState cityState) {
        return CompletableFuture.completedFuture(null);
    }
    @Override public CompletionStage<Void> removeWorld(String worldName) {
        return CompletableFuture.completedFuture(null);
    }
}
