package com.cuzz.rookiecitystate.social;

import java.util.Set;
import java.util.UUID;

public record CitySocialPlayerStatus(String week, int votesUsed, int votesRemaining,
                                     Set<UUID> qualifiedCities, Set<UUID> likedCities) {
    public CitySocialPlayerStatus {
        qualifiedCities = Set.copyOf(qualifiedCities);
        likedCities = Set.copyOf(likedCities);
    }
}
