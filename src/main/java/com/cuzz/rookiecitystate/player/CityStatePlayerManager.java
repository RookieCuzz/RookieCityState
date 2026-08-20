package com.cuzz.rookiecitystate.player;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class CityStatePlayerManager {
    private RookieCityState plugin = RookieCityState.inst();
    private Map<UUID, CityStatePlayer> cityStatePlayerMap = new HashMap<>();

    public CityStatePlayer getCityStatePlayer(@NotNull UUID uuid) {
        if (!cityStatePlayerMap.containsKey(uuid)) {
            cityStatePlayerMap.put(uuid, isRegistered(uuid) ? new CityStatePlayer(getCityStatePlayerFile(uuid)) : registerCityStatePlayer(uuid));
        }


        return cityStatePlayerMap.get(uuid);
    }

    public CityStatePlayer registerCityStatePlayer(@NotNull UUID uuid) {
        if (isRegistered(uuid)) {
            throw new IllegalArgumentException("该玩家已注册 CityStatePlayer");
        }

        File file = getCityStatePlayerFile(uuid);
        YamlConfiguration yml = new YamlConfiguration();

        yml.set("uuid", uuid.toString());
        yml.set("register_time", System.currentTimeMillis());
        YamlFiles.save(yml, file);
        return new CityStatePlayer(file);
    }

    public boolean isRegistered(@NotNull Player player) {
        return isRegistered(player.getUniqueId());
    }

    public boolean isRegistered(@NotNull UUID uuid) {
        return getCityStatePlayerFile(uuid).exists();
    }

    private File getCityStatePlayerFile(@NotNull UUID uuid) {
        return new File(plugin.getDataFolder(), "data" + File.separator + "players" + File.separator + uuid + ".yml");
    }

    public CityStatePlayer getCityStatePlayer(@NotNull Player player) {
        return getCityStatePlayer(player.getUniqueId());
    }

    public Collection<CityStatePlayer> getOnlineCityStatePlayers() {
        return cityStatePlayerMap.size() == 0 ? new ArrayList<>() : cityStatePlayerMap.values().stream().filter(CityStatePlayer::isOnline).collect(Collectors.toList());
    }

    public Collection<CityStatePlayer> getLoadedCityStatePlayers() {
        return cityStatePlayerMap.values();
    }

    public CityStatePlayer findRegisteredPlayer(@NotNull String value) {
        try {
            UUID id = UUID.fromString(value);
            return isRegistered(id) ? getCityStatePlayer(id) : null;
        } catch (IllegalArgumentException ignored) { }
        for (CityStatePlayer player : cityStatePlayerMap.values()) {
            if (player.getName().equalsIgnoreCase(value)) return player;
        }
        File[] files = new File(plugin.getDataFolder(), "data" + File.separator + "players")
                .listFiles((directory, name) -> name.endsWith(".yml"));
        if (files == null) return null;
        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlFiles.load(file);
                if (value.equalsIgnoreCase(yaml.getString("known_name"))) {
                    return getCityStatePlayer(UUID.fromString(yaml.getString("uuid")));
                }
            } catch (RuntimeException ignored) { }
        }
        return null;
    }
}
