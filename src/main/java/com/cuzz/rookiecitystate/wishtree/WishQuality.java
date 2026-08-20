package com.cuzz.rookiecitystate.wishtree;

public enum WishQuality {
    COMMON(0, 0D),
    RARE(30, 0.025D),
    EPIC(80, 0.005D);

    private final int pityLimit;
    private final double earlyChance;

    WishQuality(int pityLimit, double earlyChance) {
        this.pityLimit = pityLimit;
        this.earlyChance = earlyChance;
    }

    public int pityLimit() { return pityLimit; }
    public double earlyChance() { return earlyChance; }
}
