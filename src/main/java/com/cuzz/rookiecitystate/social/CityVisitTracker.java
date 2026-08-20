package com.cuzz.rookiecitystate.social;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class CityVisitTracker implements Listener {
    private final RookieCityState plugin;
    private final CitySocialService social;
    private final LongSupplier now;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final BukkitTask task;

    public CityVisitTracker(RookieCityState plugin, CitySocialService social) {
        this(plugin, social, System::currentTimeMillis, true);
    }

    CityVisitTracker(RookieCityState plugin, CitySocialService social, LongSupplier now, boolean schedule) {
        this.plugin = plugin;
        this.social = social;
        this.now = now;
        this.task = schedule ? Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L) : null;
    }

    public void tick() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("访客计时必须在主线程运行");
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
            CityState city = plugin.getCityWorldService().findCityByWorld(player.getWorld().getName());
            if (!social.isEligibleVisitor(player, city)) {
                sessions.remove(player.getUniqueId());
                continue;
            }
            if (social.isQualified(player, city)) {
                sessions.remove(player.getUniqueId());
                continue;
            }
            Session current = sessions.get(player.getUniqueId());
            if (current == null || !current.cityId.equals(city.getUuid())) {
                sessions.put(player.getUniqueId(), new Session(city.getUuid(), now.getAsLong()));
                continue;
            }
            long needed = Math.multiplyExact(social.getConfig().qualificationSeconds(), 1000L);
            if (now.getAsLong() - current.startedAt < needed) continue;
            social.qualify(player, city);
            sessions.remove(player.getUniqueId());
        }
        sessions.keySet().removeIf(id -> !online.contains(id));
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { sessions.remove(event.getPlayer().getUniqueId()); }

    @EventHandler public void onWorldChanged(PlayerChangedWorldEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    public void cancelCity(UUID cityId) { sessions.entrySet().removeIf(entry -> entry.getValue().cityId.equals(cityId)); }
    public void cancelPlayer(UUID playerId) { sessions.remove(playerId); }

    public void cancelInvalidSessions() {
        sessions.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            CityState city = plugin.getCityStateManager().getCityState(entry.getValue().cityId);
            return player == null || !social.isEligibleVisitor(player, city)
                    || !player.getWorld().getName().equals(city.getWorldName());
        });
    }

    public int sessionCount() { return sessions.size(); }
    public void shutdown() { sessions.clear(); if (task != null) task.cancel(); }

    private record Session(UUID cityId, long startedAt) { }
}
