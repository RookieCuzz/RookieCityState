package com.cuzz.rookiecitystate.internal.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class ItemUtil {
    private ItemUtil() {
    }

    public static boolean isValid(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
    }
}
