package com.cuzz.rookiecitystate.guardian;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GuardianBeastView(UUID cityId, GuardianSpecies species, String speciesName, GuardianForm form,
                                int level, int completedDays, String day, List<Material> favoriteFoods,
                                int fullness, int target, boolean completedToday, int feedsUsed,
                                int feedsRemaining, long availableContribution, long lifetimeContribution,
                                Map<UUID, Integer> dailyContributions, boolean visualAvailable, String visualError) {
}
