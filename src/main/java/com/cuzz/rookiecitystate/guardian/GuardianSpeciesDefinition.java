package com.cuzz.rookiecitystate.guardian;

import org.bukkit.Material;

public record GuardianSpeciesDefinition(GuardianSpecies species, String displayName, Material icon,
                                        String babyModel, String adultModel) {
}
