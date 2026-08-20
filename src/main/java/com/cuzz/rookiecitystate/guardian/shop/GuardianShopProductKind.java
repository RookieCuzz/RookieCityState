package com.cuzz.rookiecitystate.guardian.shop;

public enum GuardianShopProductKind {
    PARTICLE,
    TITLE,
    CHAT_PREFIX,
    ACTION,
    ITEM,
    MAGIC_STONE;

    public boolean permanent() {
        return this == PARTICLE || this == TITLE || this == CHAT_PREFIX || this == ACTION;
    }
}
