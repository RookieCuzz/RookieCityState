package com.cuzz.rookiecitystate.world;

import java.util.Locale;

public enum WorldVisibility {
    PRIVATE,
    PUBLIC;

    public static WorldVisibility parse(String value, WorldVisibility fallback) {
        if (value == null) return fallback;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
