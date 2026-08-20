package com.cuzz.rookiecitystate.guardian.shop;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record GuardianShopView(String cycle, long nextRotationAt, long availableContribution,
                               long lifetimeContribution, List<GuardianShopProduct> rotation,
                               Set<String> ownedProductIds, Map<GuardianCosmeticSlot, String> equipped,
                               Map<String, Integer> purchaseCounts) {
    public GuardianShopView {
        rotation = List.copyOf(rotation);
        ownedProductIds = Set.copyOf(ownedProductIds);
        equipped = Map.copyOf(equipped);
        purchaseCounts = Map.copyOf(purchaseCounts);
    }
}
