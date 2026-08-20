package com.cuzz.rookiecitystate.internal.item;

import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ItemBuilder {
    private Material material = Material.STONE;
    private boolean colored;
    private String displayName;
    private final List<String> lore = new ArrayList<>();
    private final Set<ItemFlag> flags = new LinkedHashSet<>();
    private final Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
    private String skullOwner;
    private String skullTexture;

    public ItemBuilder material(Material material) { this.material = material == null ? Material.BARRIER : material; return this; }
    public ItemBuilder material(String material) {
        Material parsed = material == null ? null : Material.matchMaterial(material);
        this.material = parsed == null ? Material.BARRIER : parsed;
        return this;
    }
    public ItemBuilder colored(boolean colored) { this.colored = colored; return this; }
    public ItemBuilder colored() { this.colored = true; return this; }
    public ItemBuilder displayName(String name) { this.displayName = name; return this; }
    public ItemBuilder lores(List<String> lores) { this.lore.clear(); if (lores != null) this.lore.addAll(lores); return this; }
    public ItemBuilder insertLore(int index, String value) { this.lore.add(Math.max(0, Math.min(index, lore.size())), value); return this; }
    public ItemBuilder itemFlags(ItemFlag... values) { if (values != null) flags.addAll(Arrays.asList(values)); return this; }
    public ItemBuilder addItemFlag(ItemFlag value) { if (value != null) flags.add(value); return this; }
    public ItemBuilder enchantment(Enchantment enchantment, int level) { if (enchantment != null) enchantments.put(enchantment, level); return this; }
    public ItemBuilder skullOwner(String owner) { this.skullOwner = owner; return this; }
    public ItemBuilder skullTexture(String texture) { this.skullTexture = texture; return this; }

    public ItemStack build() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (displayName != null) meta.displayName(component(displayName));
        if (!lore.isEmpty()) meta.lore(lore.stream().map(this::component).toList());
        if (!flags.isEmpty()) meta.addItemFlags(flags.toArray(ItemFlag[]::new));
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            meta.addEnchant(entry.getKey(), entry.getValue(), true);
        }
        if (meta instanceof SkullMeta skullMeta) {
            if (skullTexture != null && !skullTexture.isBlank()) {
                com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                profile.setProperty(new ProfileProperty("textures", skullTexture));
                skullMeta.setPlayerProfile(profile);
            } else if (skullOwner != null && !skullOwner.isBlank()) {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(skullOwner));
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    private Component component(String text) {
        String value = text == null ? "" : text;
        Component result = colored
                ? LegacyComponentSerializer.legacyAmpersand().deserialize(value.replace('§', '&'))
                : Component.text(value);
        return result.decoration(TextDecoration.ITALIC, false);
    }

    public static boolean isItemFlagEnabled() { return true; }
    public static boolean isSkullTextureEnabled() { return true; }
}
