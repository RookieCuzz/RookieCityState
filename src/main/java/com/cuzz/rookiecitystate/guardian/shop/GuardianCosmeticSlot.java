package com.cuzz.rookiecitystate.guardian.shop;

public enum GuardianCosmeticSlot {
    PARTICLE,
    TITLE,
    CHAT_PREFIX,
    ACTION;

    public GuardianShopProductKind productKind() {
        return GuardianShopProductKind.valueOf(name());
    }

    public String pathKey() { return name().toLowerCase(java.util.Locale.ROOT); }
}
