package com.cuzz.rookiecitystate.citystate;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.api.event.CityStateCreatedEvent;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.citystate.member.CityStatePosition;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CityStateManager {
    private final RookieCityState plugin = RookieCityState.inst();
    private final Map<UUID, CityState> cityStates = new HashMap<>();
    private final Map<String, CityState> names = new HashMap<>();
    private final Map<UUID, CityState> memberCityStates = new HashMap<>();

    public synchronized void createCityState(CityStatePlayer ownerPlayer, @NotNull String cityStateName) {
        if (ownerPlayer == null) throw new IllegalArgumentException("城邦创建者不能为空");
        if (ownerPlayer.isInCityState()) throw new IllegalArgumentException("创建者已经加入其他城邦");
        String normalized = normalizeName(cityStateName);
        if (normalized.isEmpty()) throw new IllegalArgumentException("城邦名不能为空");
        if (names.containsKey(normalized)) throw new IllegalArgumentException("城邦名已经存在");

        UUID uuid = UUID.randomUUID();
        File file = new File(plugin.getDataFolder(), "data" + File.separator + "city_states" + File.separator + uuid + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        yaml.set("uuid", uuid.toString());
        yaml.set("name", cityStateName);
        yaml.set("creation_time", now);
        yaml.set("member_damage_enabled", true);
        yaml.set("members." + ownerPlayer.getUuid() + ".position", CityStatePosition.OWNER.name());
        yaml.set("members." + ownerPlayer.getUuid() + ".join_time", now);
        YamlFiles.save(yaml, file);

        try {
            CityState cityState = new CityState(file);
            registerLoadedCityState(cityState);
            Bukkit.getPluginManager().callEvent(new CityStateCreatedEvent(cityState, ownerPlayer));
        } catch (RuntimeException exception) {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    public synchronized void loadCityState(@NotNull File file) {
        CityState cityState = new CityState(file);
        if (cityState.isDeleted()) return;
        registerLoadedCityState(cityState);
    }

    private void registerLoadedCityState(CityState cityState) {
        if (cityStates.containsKey(cityState.getUuid())) throw new IllegalArgumentException("城邦 UUID 重复: " + cityState.getUuid());
        String normalized = normalizeName(cityState.getName());
        if (names.containsKey(normalized)) throw new IllegalArgumentException("城邦名重复: " + cityState.getName());
        for (CityStateMember member : cityState.getMembers()) {
            CityState existing = memberCityStates.get(member.getUuid());
            if (existing != null) {
                throw new IllegalArgumentException("玩家同时存在于多个城邦: " + member.getUuid());
            }
        }
        cityStates.put(cityState.getUuid(), cityState);
        names.put(normalized, cityState);
        for (CityStateMember member : cityState.getMembers()) memberCityStates.put(member.getUuid(), cityState);
        updateCache();
    }

    public synchronized void loadCityStates() {
        unloadAll();
        File folder = new File(plugin.getDataFolder(), "data" + File.separator + "city_states");
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            try {
                loadCityState(file);
            } catch (RuntimeException exception) {
                PluginLogger.warning("跳过损坏的城邦文件 " + file.getName() + ": " + exception.getMessage());
            }
        }
    }

    public synchronized void unloadCityState(@NotNull CityState cityState) {
        if (cityStates.get(cityState.getUuid()) != cityState) throw new IllegalArgumentException("城邦未加载或实例已失效");
        cityStates.remove(cityState.getUuid());
        names.remove(normalizeName(cityState.getName()), cityState);
        for (CityStateMember member : cityState.getMembers()) memberCityStates.remove(member.getUuid(), cityState);
        cityState.setValid(false);
        updateCache();
    }

    public synchronized void unloadAll() {
        for (CityState cityState : new ArrayList<>(cityStates.values())) cityState.setValid(false);
        cityStates.clear();
        names.clear();
        memberCityStates.clear();
        updateCache();
    }

    synchronized void registerMember(CityState cityState, UUID playerId) {
        if (cityStates.get(cityState.getUuid()) != cityState) throw new IllegalStateException("城邦实例已失效");
        CityState existing = memberCityStates.putIfAbsent(playerId, cityState);
        if (existing != null && existing != cityState) throw new IllegalStateException("玩家已经加入其他城邦");
    }

    synchronized void unregisterMember(CityState cityState, UUID playerId) {
        memberCityStates.remove(playerId, cityState);
    }

    public synchronized @Nullable CityState getCityStateByMember(UUID playerId) {
        return memberCityStates.get(playerId);
    }

    public synchronized @Nullable CityState getCityStateByName(String name) {
        return names.get(normalizeName(name));
    }

    public synchronized CityState getCityState(@NotNull UUID uuid) { return cityStates.get(uuid); }
    public synchronized boolean isLoaded(@NotNull UUID uuid) { return cityStates.containsKey(uuid); }
    public synchronized boolean isValid(@Nullable CityState cityState) { return cityState != null && cityStates.get(cityState.getUuid()) == cityState; }
    public synchronized int getCityStateCount() { return cityStates.size(); }
    public synchronized Collection<CityState> getCityStates() { return List.copyOf(cityStates.values()); }

    public synchronized List<CityState> getSortedCityStates() {
        return cityStates.values().stream().sorted(Comparator.comparingInt(CityState::getRank).reversed()).toList();
    }

    public static String normalizeName(String name) {
        if (name == null) return "";
        String colored = ChatColor.translateAlternateColorCodes('&', name);
        String stripped = ChatColor.stripColor(colored);
        return (stripped == null ? "" : stripped).trim().toLowerCase(Locale.ROOT);
    }

    private void updateCache() {
        if (plugin.getCacheCityStateManager() != null) plugin.getCacheCityStateManager().updateSortedCityStates();
    }
}
