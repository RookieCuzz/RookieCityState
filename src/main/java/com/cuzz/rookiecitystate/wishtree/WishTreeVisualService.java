package com.cuzz.rookiecitystate.wishtree;

import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.world.operation.CityWorldOperation;
import org.bukkit.World;

import java.util.concurrent.CompletionStage;

public interface WishTreeVisualService {
    CompletionStage<Void> prepareAssets();
    CompletionStage<Void> pasteInitial(CityState cityState, World world, CityWorldOperation operation);
    CompletionStage<Void> requestLevel(CityState cityState, int level);
    void ensureInteraction(CityState cityState, World world);
    void removeInteraction(CityState cityState);
    void shutdown();
}
