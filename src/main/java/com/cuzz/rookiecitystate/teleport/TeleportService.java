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
    private static final double MOVEMENT_TOLERANCE_SQUARED = 0.01D * 0.01D;

    private enum Phase { COUNTDOWN, TELEPORTING, TERMINAL }

    private static final class Session {
        private final long generation;
        private final Location origin;
        private final Runnable success;
        private final Runnable cancelled;
        private final Consumer<Throwable> failure;
        private BukkitTask task;
        private Phase phase;

        private Session(long generation, Location origin, Runnable success,
                        Runnable cancelled, Consumer<Throwable> failure, Phase phase) {
            this.generation = generation;
            this.origin = origin;
            this.success = success;
            this.cancelled = cancelled;
            this.failure = failure;
            this.phase = phase;
        }
    }

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
        Session session = new Session(generation, player.getLocation().clone(), success,
                cancelled, failure, waitSeconds <= 0 ? Phase.TELEPORTING : Phase.COUNTDOWN);
        sessions.put(player.getUniqueId(), session);
        if (waitSeconds <= 0) {
            doTeleport(player, target, session);
            return true;
        }
        final int[] elapsed = {0};
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline() || sessions.get(player.getUniqueId()) != session
                        || generations.getOrDefault(player.getUniqueId(), -1L) != generation) {
                    TeleportService.this.cancel(player.getUniqueId(), false);
                    return;
                }
                if (elapsed[0] >= waitSeconds) {
                    cancel();
                    session.task = null;
                    session.phase = Phase.TELEPORTING;
                    doTeleport(player, target, session);
                    return;
                }
                countdown.accept(waitSeconds - elapsed[0]);
                elapsed[0]++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        session.task = task;
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
        Session session = new Session(generation, player.getLocation().clone(), success,
                () -> { }, failure, Phase.TELEPORTING);
        sessions.put(player.getUniqueId(), session);
        doTeleport(player, destination.clone(), session);
        return true;
    }

    private void doTeleport(Player player, Location destination, Session session) {
        player.teleportAsync(destination).whenComplete((teleported, throwable) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sessions.get(player.getUniqueId()) != session
                        || generations.getOrDefault(player.getUniqueId(), -1L) != session.generation) {
                    return;
                }
                if (throwable != null || !Boolean.TRUE.equals(teleported)) {
                    finishFailure(player.getUniqueId(), session,
                            throwable == null ? new IllegalStateException("异步传送返回失败") : throwable);
                } else {
                    finishSuccess(player.getUniqueId(), session);
                }
            });
        });
    }

    public void handleMove(Player player, Location from, Location to) {
        if (to == null) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (session.phase != Phase.COUNTDOWN) return;
        Location origin = session.origin;
        if (origin.getWorld() != to.getWorld() || origin.distanceSquared(to) > MOVEMENT_TOLERANCE_SQUARED) {
            cancel(player.getUniqueId(), true);
        }
    }

    public void cancel(Player player, boolean notify) { cancel(player.getUniqueId(), notify); }

    private void cancel(UUID playerId, boolean notify) {
        Session session = sessions.remove(playerId);
        generations.merge(playerId, 1L, Long::sum);
        if (session == null) return;
        if (session.task != null) session.task.cancel();
        if (session.phase == Phase.TERMINAL) return;
        session.phase = Phase.TERMINAL;
        try {
            session.cancelled.run();
        } catch (Throwable error) {
            PluginLogger.error("传送取消回调失败", error instanceof RuntimeException runtime
                    ? runtime : new RuntimeException(error));
        }
    }

    public void cancelAll() {
        cancelAll(true);
    }

    public void cancelAll(boolean invokeCancelledCallbacks) {
        for (UUID playerId : sessions.keySet().toArray(UUID[]::new)) cancel(playerId, invokeCancelledCallbacks);
        sessions.clear();
        generations.clear();
    }

    public boolean hasPending(Player player) { return sessions.containsKey(player.getUniqueId()); }

    public int getPendingCount() { return sessions.size(); }

    private void finishSuccess(UUID playerId, Session session) {
        if (!finish(playerId, session)) return;
        session.success.run();
    }

    private void finishFailure(UUID playerId, Session session, Throwable failure) {
        if (!finish(playerId, session)) return;
        session.failure.accept(failure);
    }

    private boolean finish(UUID playerId, Session session) {
        if (sessions.get(playerId) != session || session.phase == Phase.TERMINAL) return false;
        sessions.remove(playerId);
        if (session.task != null) session.task.cancel();
        session.phase = Phase.TERMINAL;
        return true;
    }

    private boolean isAvailable(Location location) {
        World world = location == null ? null : location.getWorld();
        return world != null && Bukkit.getWorld(world.getUID()) != null;
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("传送服务只能在主线程调用");
    }
}
