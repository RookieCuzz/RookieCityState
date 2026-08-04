package com.cuzz.rookiecitystate.citystate;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class CityStateIcon {
    private CityState cityState;
    private UUID uuid;
    private String displayName;
    private Material material;
    private String firstLore;

    public CityStateIcon(@NotNull CityState cityState, @NotNull UUID uuid) {
        this.cityState = cityState;
        this.uuid = uuid;

        ConfigurationSection iconSection = cityState.getYaml().getConfigurationSection("icons").getConfigurationSection(uuid.toString());

        this.material = Material.valueOf(iconSection.getString("material"));
        this.firstLore = iconSection.getString("first_lore");
        this.displayName = iconSection.getString("display_name");
    }

    public UUID getUuid() {
        return uuid;
    }

    public CityState getCityState() {
        return cityState;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public String getFirstLore() {
        return firstLore;
    }

    public boolean isValid() {
        return cityState.getIcons().stream().anyMatch(icon -> icon == this);
    }
}
