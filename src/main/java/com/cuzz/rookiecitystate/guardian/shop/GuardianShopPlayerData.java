package com.cuzz.rookiecitystate.guardian.shop;

import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class GuardianShopPlayerData {
    private static final String ROOT = "guardian_beast.shop";
    private static final String CONTRIBUTION = "guardian_beast.contribution.available";
    private final CityStatePlayer player;

    GuardianShopPlayerData(CityStatePlayer player) { this.player = player; }

    long available() { return Math.max(0L, player.getYaml().getLong(CONTRIBUTION, 0L)); }
    long lifetime() { return Math.max(0L, player.getYaml().getLong("guardian_beast.contribution.lifetime", 0L)); }

    void spend(long price) {
        if (price < 1 || available() < price) throw new IllegalArgumentException("可用贡献不足");
        player.getYaml().set(CONTRIBUTION, Math.subtractExact(available(), price));
    }

    boolean owns(String productId) { return player.getYaml().isConfigurationSection(ROOT + ".owned." + productId); }

    void own(GuardianShopProduct product, long purchasedAt) {
        if (!product.permanent()) throw new IllegalArgumentException("消耗品不能加入永久收藏");
        String path = ROOT + ".owned." + product.id();
        player.getYaml().set(path, null);
        ConfigurationSection section = player.getYaml().createSection(path);
        product.save(section);
        section.set("purchased_at", purchasedAt);
    }

    GuardianShopProduct owned(String productId) {
        ConfigurationSection section = player.getYaml().getConfigurationSection(ROOT + ".owned." + productId);
        if (section == null) return null;
        try { return GuardianShopProduct.loadSnapshot(section); }
        catch (RuntimeException ignored) { return null; }
    }

    Map<String, GuardianShopProduct> owned() {
        Map<String, GuardianShopProduct> result = new LinkedHashMap<>();
        ConfigurationSection root = player.getYaml().getConfigurationSection(ROOT + ".owned");
        if (root == null) return result;
        for (String id : root.getKeys(false)) {
            GuardianShopProduct product = owned(id);
            if (product != null) result.put(id, product);
        }
        return result;
    }

    Set<String> ownedIds() { return new LinkedHashSet<>(owned().keySet()); }

    String equipped(GuardianCosmeticSlot slot) {
        String id = player.getYaml().getString(ROOT + ".equipped." + slot.pathKey());
        GuardianShopProduct product = id == null ? null : owned(id);
        return product != null && product.kind() == slot.productKind() ? id : null;
    }

    Map<GuardianCosmeticSlot, String> equipped() {
        Map<GuardianCosmeticSlot, String> result = new EnumMap<>(GuardianCosmeticSlot.class);
        for (GuardianCosmeticSlot slot : GuardianCosmeticSlot.values()) {
            String id = equipped(slot);
            if (id != null) result.put(slot, id);
        }
        return result;
    }

    void equip(GuardianCosmeticSlot slot, String productId) {
        GuardianShopProduct product = owned(productId);
        if (product == null || product.kind() != slot.productKind()) throw new IllegalArgumentException("未拥有对应装扮");
        player.getYaml().set(ROOT + ".equipped." + slot.pathKey(), productId);
    }

    void unequip(GuardianCosmeticSlot slot) { player.getYaml().set(ROOT + ".equipped." + slot.pathKey(), null); }

    int purchaseCount(String cycle, String productId) {
        return Math.max(0, player.getYaml().getInt(ROOT + ".purchase_limits." + cycle + "." + productId, 0));
    }

    Map<String, Integer> purchaseCounts(String cycle, Iterable<GuardianShopProduct> products) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (GuardianShopProduct product : products) result.put(product.id(), purchaseCount(cycle, product.id()));
        return result;
    }

    void recordPurchase(String cycle, String productId) {
        String path = ROOT + ".purchase_limits." + cycle + "." + productId;
        player.getYaml().set(path, Math.addExact(purchaseCount(cycle, productId), 1));
        pruneLimits(cycle);
    }

    void revoke(String productId) {
        GuardianShopProduct product = owned(productId);
        if (product == null) throw new IllegalArgumentException("玩家未拥有该永久商品");
        GuardianCosmeticSlot slot = product.slot();
        String equipped = equipped(slot);
        player.getYaml().set(ROOT + ".owned." + productId, null);
        if (productId.equals(equipped)) unequip(slot);
    }

    void resetLimits() { player.getYaml().set(ROOT + ".purchase_limits", null); }

    String snapshot() { return player.getYaml().saveToString(); }
    void restore(String snapshot) {
        try { player.getYaml().loadFromString(snapshot); }
        catch (InvalidConfigurationException error) { throw new IllegalStateException("无法恢复灵兽商店玩家快照", error); }
    }
    void save() { player.save(); }

    private void pruneLimits(String currentCycle) {
        ConfigurationSection section = player.getYaml().getConfigurationSection(ROOT + ".purchase_limits");
        if (section == null) return;
        for (String cycle : new java.util.ArrayList<>(section.getKeys(false))) if (!cycle.equals(currentCycle)) section.set(cycle, null);
    }
}
