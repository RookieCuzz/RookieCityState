package com.cuzz.rookiecitystate.citystate.member;

/**
 * 职位
 */
public enum CityStatePosition {
    MEMBER(0), OWNER(1);

    int level;

    CityStatePosition(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
