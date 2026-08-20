package com.cuzz.rookiecitystate.wishtree;

import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.world.operation.CityWorldOperation;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class NoopWishTreeVisualService implements WishTreeVisualService {
    private final WishTreeStore store;
    public NoopWishTreeVisualService(WishTreeStore store) { this.store = store; }
    @Override public CompletionStage<Void> prepareAssets() { return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> pasteInitial(CityState cityState, World world, CityWorldOperation operation) {
        store.get(cityState).completeVisualUpgrade(1);
        return CompletableFuture.completedFuture(null);
    }
    @Override public CompletionStage<Void> requestLevel(CityState cityState, int level) {
        store.get(cityState).completeVisualUpgrade(level);
        return CompletableFuture.completedFuture(null);
    }
    @Override public void ensureInteraction(CityState cityState, World world) { }
    @Override public void removeInteraction(CityState cityState) { }
    @Override public void shutdown() { }
}
