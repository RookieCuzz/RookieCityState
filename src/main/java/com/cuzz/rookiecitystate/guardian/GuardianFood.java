package com.cuzz.rookiecitystate.guardian;

import org.bukkit.Material;

public record GuardianFood(Material material, int nourishment, int contribution, boolean enabled) {
    public GuardianFood {
        if (material == null || material.isAir()) throw new IllegalArgumentException("食物材质无效");
        if (nourishment < 1 || contribution < 1) throw new IllegalArgumentException("食物数值必须为正整数");
    }
}
