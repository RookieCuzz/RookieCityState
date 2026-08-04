package com.cuzz.rookiecitystate.config.gui.item;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.internal.item.ItemBuilder;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.placeholder.PlaceholderText;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GUIItemManager {
    private GUIItemManager() {
    }

    public static PriorityItem getPriorityItem(@NotNull ConfigurationSection section) {
        return getPriorityItem(section, null, null);
    }

    public static PriorityItem getPriorityItem(@NotNull ConfigurationSection section, @Nullable OfflinePlayer player) {
        return getPriorityItem(section, player, null);
    }

    public static PriorityItem getPriorityItem(@NotNull ConfigurationSection section,
                                               @Nullable PlaceholderContainer placeholders) {
        return getPriorityItem(section, null, placeholders);
    }

    public static PriorityItem getPriorityItem(@NotNull ConfigurationSection section, @Nullable OfflinePlayer player,
                                               @Nullable PlaceholderContainer placeholders) {
        if (!section.getBoolean("enabled", true)) return null;
        return new PriorityItem(section.getInt("priority"),
                getItemBuilder(section.getConfigurationSection("icon"), player, placeholders));
    }

    public static IndexItem getIndexItem(@NotNull ConfigurationSection section) {
        return getIndexItem(section, null, null);
    }

    public static IndexItem getIndexItem(@NotNull ConfigurationSection section,
                                         @Nullable PlaceholderContainer placeholders) {
        return getIndexItem(section, null, placeholders);
    }

    public static IndexItem getIndexItem(@NotNull ConfigurationSection section, @Nullable OfflinePlayer player) {
        return getIndexItem(section, player, null);
    }

    public static IndexItem getIndexItem(@NotNull ConfigurationSection section, @Nullable OfflinePlayer player,
                                         @Nullable PlaceholderContainer placeholders) {
        if (!section.getBoolean("enabled", true)) return null;
        int index = section.getInt("index", 0);
        if (index <= 0) throw new IllegalArgumentException("无效 GUI 槽位: " + section.getCurrentPath());
        return new IndexItem(index - 1,
                getItemBuilder(section.getConfigurationSection("icon"), player, placeholders));
    }

    public static ItemBuilder getItemBuilder(@Nullable ConfigurationSection section, @Nullable OfflinePlayer player) {
        return getItemBuilder(section, player, null);
    }

    public static ItemBuilder getItemBuilder(@Nullable ConfigurationSection section, @Nullable OfflinePlayer player,
                                             @Nullable PlaceholderContainer placeholders) {
        if (section == null) {
            PluginLogger.warning("GUI 图标配置缺失，将显示 BARRIER。");
            return invalidIcon("未知路径");
        }
        boolean usePapi = section.getBoolean("use_papi", MainSettings.isCityStateGuiDefaultUsePapi());
        String configuredMaterial = section.getString("material");
        Material material = configuredMaterial == null ? null : Material.matchMaterial(configuredMaterial);
        if (material == null || material.isAir()) {
            PluginLogger.warning("无效 GUI 材料，路径: " + section.getCurrentPath() + "，值: " + configuredMaterial);
            return invalidIcon(section.getCurrentPath());
        }

        ItemBuilder builder = new ItemBuilder().material(material)
                .colored(section.getBoolean("colored", MainSettings.isCityStateGuiDefaultColored()));
        if (MainSettings.isCityStateGuiDefaultHideAllFlags()) {
            builder.itemFlags(ItemFlag.values());
        } else if (section.contains("flags")) {
            for (String name : section.getStringList("flags")) {
                if (name.equals("*")) {
                    builder.itemFlags(ItemFlag.values());
                    break;
                }
                try {
                    builder.addItemFlag(ItemFlag.valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException exception) {
                    PluginLogger.warning("无效 ItemFlag，路径: " + section.getCurrentPath() + "，值: " + name);
                }
            }
        }

        OfflinePlayer papiPlayer = usePapi ? player : null;
        if (section.contains("display_name")) {
            builder.displayName(replace(section.getString("display_name", ""), placeholders, papiPlayer));
        }
        if (section.contains("lores")) {
            builder.lores(replace(section.getStringList("lores"), placeholders, papiPlayer));
        }

        if (section.contains("skull_owner") || section.contains("skull_texture")) {
            if (material != Material.PLAYER_HEAD) {
                PluginLogger.warning("头颅属性只能用于 PLAYER_HEAD，路径: " + section.getCurrentPath());
            } else {
                if (section.contains("skull_owner")) builder.skullOwner(section.getString("skull_owner"));
                if (section.contains("skull_texture")) builder.skullTexture(section.getString("skull_texture"));
            }
        }

        ConfigurationSection enchantments = section.getConfigurationSection("enchantments");
        if (enchantments != null) {
            for (String name : enchantments.getKeys(false)) {
                Enchantment enchantment = Registry.ENCHANTMENT.get(
                        NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
                if (enchantment == null) {
                    PluginLogger.warning("无效附魔，路径: " + enchantments.getCurrentPath() + "，值: " + name);
                    continue;
                }
                int level = enchantments.getInt(name, 1);
                if (level <= 0) {
                    PluginLogger.warning("附魔等级必须大于 0，路径: " + enchantments.getCurrentPath() + "." + name);
                    continue;
                }
                builder.enchantment(enchantment, level);
            }
        }
        return builder;
    }

    private static ItemBuilder invalidIcon(String path) {
        return new ItemBuilder().material(Material.BARRIER).colored()
                .displayName("&c图标配置错误")
                .lores(List.of("&7路径: " + path));
    }

    private static String replace(@NotNull String text, @Nullable PlaceholderContainer placeholders,
                                  @Nullable OfflinePlayer player) {
        String result = placeholders == null ? text : PlaceholderText.replacePlaceholders(text, placeholders);
        if (player != null && RookieCityState.inst().isPlaceHolderAPIEnabled()) {
            result = PlaceholderAPI.setPlaceholders(player, result);
        }
        return result;
    }

    private static List<String> replace(@NotNull List<String> values, @Nullable PlaceholderContainer placeholders,
                                        @Nullable OfflinePlayer player) {
        List<String> result = new ArrayList<>(values.size());
        for (String value : values) result.add(replace(value, placeholders, player));
        return result;
    }
}
