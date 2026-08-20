package com.cuzz.rookiecitystate.world;

import com.cuzz.rookiecitystate.citystate.CityState;
import org.bukkit.World;

import java.util.concurrent.CompletionStage;

public interface CityWorldProtectionService {
    CompletionStage<Void> ensureTemplateCore(World template, String coreRegionId,
                                             int minX, int minY, int minZ,
                                             int maxX, int maxY, int maxZ);

    void captureTemplate(World template, String coreRegionId);

    CompletionStage<Void> installTemplateSnapshot(World snapshot);

    boolean hasTemplateSnapshot();

    CompletionStage<Void> apply(CityState cityState, World world, int borderSize);

    CompletionStage<Void> synchronizeMembers(CityState cityState);

    /** Returns true only when the persisted protection snapshot matches the current city roster. */
    default boolean membersSynchronized(CityState cityState) { return true; }

    /** Removes persistent protection records before a managed world is permanently archived or deleted. */
    CompletionStage<Void> removeWorld(String worldName);
}
