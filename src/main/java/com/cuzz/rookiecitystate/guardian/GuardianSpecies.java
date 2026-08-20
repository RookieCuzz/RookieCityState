package com.cuzz.rookiecitystate.guardian;

import java.util.Locale;

public enum GuardianSpecies {
    FIRE(1), FOREST(2), FROST(3);

    private final int id;

    GuardianSpecies(int id) { this.id = id; }

    public int id() { return id; }

    public static GuardianSpecies parse(String value) {
        if (value == null || value.isBlank()) return null;
        for (GuardianSpecies species : values()) {
            if (species.name().equalsIgnoreCase(value) || Integer.toString(species.id).equals(value)) return species;
        }
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
