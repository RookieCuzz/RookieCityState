package com.cuzz.rookiecitystate.teleport;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class TeleportService {
    private record Session(Location origin, BukkitTask task, Runnable cancelled) { }

    private final RookieCityState plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> generations = new HashMap<>();

    public TeleportService(RookieCityState plugin) {
        this.plugin = plugin;
    }

    public boolean begin(Player player, Location destination, int waitSeconds, IntConsumer countdown,
                         Runnable success, Runnable cancelled, Consumer<Throwable> failure) {
        requireMainThread();
        if (!isAvailable(destination)) {
            failure.accept(new IllegalStateException("目标世界不可用"));
            return false;
        }
        cancel(player.getUniqueId(), false);
        long generation = generations.merge(player.getUniqueId(), 1L, Long::sum);
        Location target = destination.clone();
        if (waitSeconds <= 0) {
            doTeleport(player, target, generation, success, failure);
            return true;
        }
        final int[] elapsed = {0};
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline() || generations.getOrDefault(player.getUniqueId(), -1L) != generation) {
                    cancel();
                    return;
                }
                if (elapsed[0] >= waitSeconds) {
                    sessions.remove(player.getUniqueId());
                    cancel();
                    doTeleport(player, target, generation, success, failure);
                    return;
                }
                countdown.accept(waitSeconds - elapsed[0]);
                elapsed[0]++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        sessions.put(player.getUniqueId(), new Session(player.getLocation().clone(), task, cancelled));
        return true;
    }

    public boolean teleport(Player player, Location destination, Runnable success, Consumer<Throwable> failure) {
        requireMainThread();
        if (!isAvailable(destination)) {
            failure.accept(new IllegalStateException("目标世界不可用"));
            return false;
        }
        cancel(player.getUniqueId(), false);
        long generation = generations.merge(player.getUniqueId(), 1L, Long::sum);
        doTeleport(player, destination.clone(), generation, success, failure);
        return true;
    }

    private void doTeleport(Player player, Location destination, long generation,
                            Runnable success, Consumer<Throwable> failure) {
        player.teleportAsync(destination).whenComplete((teleported, throwable) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (generations.getOrDefault(player.getUniqueId(), -1L) != generation) return;
                if (throwable != null || !Boolean.TRUE.equals(teleported)) {
                    failure.accept(throwable == null ? new IllegalStateException("异步传送返回失败") : throwable);
                } else {
                    success.run();
                }
            });
        });
    }

    public void handleMove(Player player, Location from, Location to) {
        if (to == null || !sessions.containsKey(player.getUniqueId())) return;
        if (from.getWorld() != to.getWorld() || from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            cancel(player.getUniqueId(), true);
        }
    }

    public void cancel(Player player, boolean notify) { cancel(player.getUniqueId(), notify); }

    private void cancel(UUID playerId, boolean notify) {
        Session session = sessions.remove(playerId);
        generations.merge(playerId, 1L, Long::sum);
        if (session == null) return;
        session.task().cancel();
        if (notify) session.cancelled().run();
    }

    public void cancelAll() {
        for (UUID playerId : sessions.keySet().toArray(UUID[]::new)) cancel(playerId, false);
        sessions.clear();
        generations.clear();
    }

    public boolean hasPending(Player player) { return sessions.containsKey(player.getUniqueId()); }

    public int getPendingCount() { return sessions.size(); }

    private boolean isAvailable(Location location) {
        World world = location == null ? null : location.getWorld();
        return world != null && Bukkit.getWorld(world.getUID()) != null;
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("传送服务只能在主线程调用");
    }
}
