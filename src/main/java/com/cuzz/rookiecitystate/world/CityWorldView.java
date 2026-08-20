package com.cuzz.rookiecitystate.world;

import java.util.UUID;

public record CityWorldView(
        UUID cityStateId,
        String worldName,
        CityLifecycleState lifecycleState,
        CityWorldState worldState,
        WorldVisibility visibility,
        boolean loaded,
        String lastError
) {
}
