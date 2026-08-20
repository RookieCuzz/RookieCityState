package com.cuzz.rookiecitystate.guardian.shop;

import java.util.List;

public record GuardianShopRotation(String cycle, long seed, long generatedAt, int configRevision,
                                   List<GuardianShopProduct> products) {
    public GuardianShopRotation { products = List.copyOf(products); }
    public GuardianShopProduct product(String id) {
        return products.stream().filter(product -> product.id().equals(id)).findFirst().orElse(null);
    }
}
