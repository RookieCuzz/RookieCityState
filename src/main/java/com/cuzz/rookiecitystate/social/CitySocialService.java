package com.cuzz.rookiecitystate.social;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.api.event.CityLikedEvent;
import com.cuzz.rookiecitystate.api.event.CityVisitQualifiedEvent;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.world.CityLifecycleState;
import com.cuzz.rookiecitystate.world.CityWorldState;
import com.cuzz.rookiecitystate.world.WorldVisibility;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;

public final class CitySocialService {
    private final RookieCityState plugin;
    private final CitySocialStore store;
    private final SocialVoteLedger voterLedger;
    private final LongSupplier now;
    private final Map<UUID, Set<UUID>> weeklyVotes = new HashMap<>();
    private volatile CitySocialConfig config;
    private volatile List<CityPopularityEntry> popular = List.of();
    private String indexedWeek;
    private String indexedDay;
    private CityVisitTracker tracker;

    public CitySocialService(RookieCityState plugin, CitySocialConfig config) {
        this(plugin, config, System::currentTimeMillis);
    }

    CitySocialService(RookieCityState plugin, CitySocialConfig config, LongSupplier now) {
        this.plugin = plugin;
        this.config = config;
        this.now = now;
        this.store = new CitySocialStore(plugin);
        this.voterLedger = new SocialVoteLedger(plugin);
    }

    public synchronized void load() {
        requireMain();
        store.loadAll();
        rebuildIndexes();
    }

    public synchronized void reloadConfig(CitySocialConfig next) {
        requireMain();
        this.config = next;
        rebuildIndexes();
        if (tracker != null) tracker.cancelInvalidSessions();
    }

    public CitySocialConfig getConfig() { return config; }

    public synchronized CitySocialView getView(Player viewer, CityState city) {
        refreshBoundary();
        String error = store.error(city.getUuid());
        int used = viewer == null ? 0 : votesUsed(viewer.getUniqueId());
        int remaining = Math.max(0, config.weeklyLikeLimit() - used);
        if (error != null) return new CitySocialView(city.getUuid(), CitySocialView.Status.ERROR, error,
                0L, 0, 0, 0L, 0, false, false, used, remaining,
                config.clock().nextWeekStart(now.getAsLong()));
        CitySocialState state = store.state(city);
        Metrics metrics = metrics(state);
        boolean qualified = viewer != null && state.isQualified(viewer.getUniqueId(), indexedWeek);
        boolean liked = viewer != null && state.hasLiked(viewer.getUniqueId(), indexedWeek);
        int rank = popular.stream().filter(entry -> entry.cityState().getUuid().equals(city.getUuid()))
                .mapToInt(CityPopularityEntry::rank).findFirst().orElse(0);
        return new CitySocialView(city.getUuid(), CitySocialView.Status.READY, null, state.totalLikes(),
                metrics.visitors, metrics.likes, metrics.score, rank, qualified, liked, used, remaining,
                config.clock().nextWeekStart(now.getAsLong()));
    }

    public synchronized List<CityPopularityEntry> getPopularCities() {
        refreshBoundary();
        return List.copyOf(popular);
    }

    public synchronized boolean isQualified(Player player, CityState city) {
        refreshBoundary();
        if (player == null || city == null || store.error(city.getUuid()) != null) return false;
        return store.state(city).isQualified(player.getUniqueId(), indexedWeek);
    }

    public CompletionStage<CityLikeResult> like(Player player, CityState city) {
        return onMain(() -> doLike(player, city));
    }

    synchronized boolean qualify(Player player, CityState city) {
        requireMain();
        refreshBoundary();
        if (!isEligibleVisitor(player, city) || !player.getWorld().getName().equals(city.getWorldName())) return false;
        if (store.error(city.getUuid()) != null) return false;
        CitySocialState state = store.state(city);
        boolean changed;
        try {
            changed = state.qualify(player.getUniqueId(), indexedDay, indexedWeek, now.getAsLong());
        } catch (RuntimeException error) {
            PluginLogger.error("保存访客参观资格失败: " + city.getName(), error);
            return false;
        }
        if (!changed) return false;
        refreshPopular();
        CityStatePlayer visitor = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        Bukkit.getPluginManager().callEvent(new CityVisitQualifiedEvent(city, visitor));
        Util.sendMsg(player, "&a你已完成对 &f" + city.getName()
                + " &a的参观，可在城邦详情页为它点赞；资格保留到下周一04:00。");
        return true;
    }

    public synchronized CitySocialPlayerStatus getPlayerStatus(UUID playerId) {
        refreshBoundary();
        Set<UUID> qualified = new HashSet<>();
        Set<UUID> liked = new HashSet<>();
        for (CityState city : plugin.getCityStateManager().getCityStates()) {
            if (store.error(city.getUuid()) != null) continue;
            CitySocialState state = store.state(city);
            if (state.isQualified(playerId, indexedWeek)) qualified.add(city.getUuid());
            if (state.hasLiked(playerId, indexedWeek)) liked.add(city.getUuid());
        }
        int used = weeklyVotes.getOrDefault(playerId, Set.of()).size();
        return new CitySocialPlayerStatus(indexedWeek, used,
                Math.max(0, config.weeklyLikeLimit() - used), qualified, liked);
    }

    public synchronized boolean revoke(CityState city, UUID playerId, String week) {
        requireMain();
        store.backup(city, "revoke-like");
        boolean changed = store.state(city).revoke(playerId, week);
        if (changed) rebuildIndexes();
        return changed;
    }

    public synchronized void resetRecent(CityState city) {
        requireMain();
        store.backup(city, "reset-recent");
        store.state(city).resetRecent();
        rebuildIndexes();
    }

    public synchronized void resetAll(CityState city) {
        requireMain();
        store.backup(city, "reset-all");
        store.state(city).resetAll();
        rebuildIndexes();
    }

    public synchronized void rebuild() { requireMain(); store.loadAll(); rebuildIndexes(); }

    public synchronized void onVisibilityChanged(CityState city) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> onVisibilityChanged(city));
            return;
        }
        if (tracker != null) tracker.cancelInvalidSessions();
        refreshPopular();
    }

    public synchronized void onMembershipChanged(UUID playerId) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> onMembershipChanged(playerId));
            return;
        }
        if (tracker != null) tracker.cancelPlayer(playerId);
    }

    public synchronized void archive(CityState city) {
        requireMain();
        if (tracker != null) tracker.cancelCity(city.getUuid());
        store.archive(city);
        rebuildIndexes();
    }

    public synchronized void refresh() {
        requireMain();
        refreshBoundary();
        refreshPopular();
    }

    public synchronized void setTracker(CityVisitTracker tracker) { this.tracker = tracker; }
    public synchronized void shutdown() { if (tracker != null) tracker.shutdown(); weeklyVotes.clear(); popular = List.of(); }

    boolean isEligibleVisitor(Player player, CityState city) {
        return player != null && player.isOnline() && city != null
                && city.getLifecycleState() == CityLifecycleState.ACTIVE
                && city.getWorldState() == CityWorldState.READY
                && city.getWorldVisibility() == WorldVisibility.PUBLIC
                && !city.isDeleted() && city.getMember(player.getUniqueId()) == null;
    }

    private synchronized CityLikeResult doLike(Player player, CityState city) {
        requireMain();
        refreshBoundary();
        int used = votesUsed(player.getUniqueId());
        int remaining = Math.max(0, config.weeklyLikeLimit() - used);
        if (!isPublicReady(city)) return CityLikeResult.failed(CityLikeResult.Status.CITY_UNAVAILABLE,
                "该城邦当前未公开或世界不可用", totalOrZero(city), remaining);
        if (city.getMember(player.getUniqueId()) != null) return CityLikeResult.failed(CityLikeResult.Status.OWN_CITY,
                "成员不能给自己的城邦点赞", totalOrZero(city), remaining);
        String error = store.error(city.getUuid());
        if (error != null) return CityLikeResult.failed(CityLikeResult.Status.SOCIAL_UNAVAILABLE,
                "该城邦社交数据不可用", 0L, remaining);
        CitySocialState state = store.state(city);
        if (!state.isQualified(player.getUniqueId(), indexedWeek)) return CityLikeResult.failed(
                CityLikeResult.Status.NOT_QUALIFIED, "请先在该城邦连续参观"
                        + config.qualificationSeconds() + "秒", state.totalLikes(), remaining);
        if (state.hasLiked(player.getUniqueId(), indexedWeek)) return CityLikeResult.failed(
                CityLikeResult.Status.ALREADY_LIKED, "本周已经给该城邦点过赞", state.totalLikes(), remaining);
        if (used >= config.weeklyLikeLimit()) return CityLikeResult.failed(CityLikeResult.Status.WEEKLY_LIMIT,
                "本周点赞额度已用完", state.totalLikes(), 0);
        try {
            if (!voterLedger.reserve(player.getUniqueId(), city.getUuid(), indexedWeek, config.weeklyLikeLimit())) {
                return CityLikeResult.failed(CityLikeResult.Status.WEEKLY_LIMIT,
                        "本周点赞额度已用完或正在结算", state.totalLikes(), 0);
            }
        } catch (RuntimeException ledgerError) {
            PluginLogger.error("保存玩家周票预留失败: " + player.getUniqueId(), ledgerError);
            return CityLikeResult.failed(CityLikeResult.Status.SAVE_FAILED,
                    "点赞额度保存失败，请稍后再试", state.totalLikes(), remaining);
        }
        try {
            if (!state.like(player.getUniqueId(), indexedDay, indexedWeek, now.getAsLong())) {
                voterLedger.rollback(player.getUniqueId(), city.getUuid(), indexedWeek);
                return CityLikeResult.failed(CityLikeResult.Status.ALREADY_LIKED,
                        "本周已经给该城邦点过赞", state.totalLikes(), remaining);
            }
        } catch (RuntimeException saveError) {
            try { voterLedger.rollback(player.getUniqueId(), city.getUuid(), indexedWeek); }
            catch (RuntimeException rollbackError) { saveError.addSuppressed(rollbackError); }
            PluginLogger.error("保存城邦点赞失败: " + city.getName(), saveError);
            return CityLikeResult.failed(CityLikeResult.Status.SAVE_FAILED,
                    "点赞保存失败，请稍后再试", state.totalLikes(), remaining);
        }
        try {
            voterLedger.commit(player.getUniqueId(), city.getUuid(), indexedWeek);
        } catch (RuntimeException ledgerError) {
            PluginLogger.error("城邦点赞已保存，但玩家周票提交失败；将由启动迁移补记: " + city.getName(), ledgerError);
            return CityLikeResult.failed(CityLikeResult.Status.SAVE_FAILED,
                    "点赞已记录，额度正在恢复，请勿重复操作", state.totalLikes(), 0);
        }
        weeklyVotes.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(city.getUuid());
        refreshPopular();
        int after = Math.max(0, config.weeklyLikeLimit() - votesUsed(player.getUniqueId()));
        CityLikeResult result = new CityLikeResult(CityLikeResult.Status.SUCCESS, "点赞成功", state.totalLikes(), after);
        CityStatePlayer visitor = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        Bukkit.getPluginManager().callEvent(new CityLikedEvent(city, visitor, result));
        city.broadcastMessage("&d" + player.getName() + " &f点赞了城邦，历史总赞达到 &d" + state.totalLikes() + "&f！");
        return result;
    }

    private void rebuildIndexes() {
        indexedWeek = config.clock().week(now.getAsLong());
        indexedDay = config.clock().day(now.getAsLong());
        weeklyVotes.clear();
        for (CitySocialState state : store.loadedStates()) {
            for (UUID voter : state.voters(indexedWeek)) {
                voterLedger.migrateCommitted(voter, state.cityId(), indexedWeek);
            }
        }
        for (UUID voter : voterLedger.voters(indexedWeek)) {
            weeklyVotes.put(voter, new HashSet<>(voterLedger.votes(voter, indexedWeek)));
        }
        pruneDetails();
        refreshPopular();
    }

    private void refreshBoundary() {
        String week = config.clock().week(now.getAsLong());
        String day = config.clock().day(now.getAsLong());
        if (!week.equals(indexedWeek) || !day.equals(indexedDay)) rebuildIndexes();
    }

    private void pruneDetails() {
        String oldest = LocalDate.parse(indexedDay).minusDays(config.retentionDays() - 1L).toString();
        for (CitySocialState state : store.loadedStates()) {
            try { state.prune(oldest); }
            catch (RuntimeException error) { PluginLogger.warning("清理城邦社交明细失败 " + state.cityId() + ": " + error.getMessage()); }
        }
    }

    private void refreshPopular() {
        List<Unranked> entries = new ArrayList<>();
        for (CityState city : plugin.getCityStateManager().getCityStates()) {
            if (!isPublicReady(city) || store.error(city.getUuid()) != null) continue;
            CitySocialState state = store.state(city);
            Metrics metrics = metrics(state);
            entries.add(new Unranked(city, metrics.score, metrics.visitors, metrics.likes, state.totalLikes()));
        }
        entries.sort(Comparator.comparingLong(Unranked::score).reversed()
                .thenComparing(Comparator.comparingInt(Unranked::likes).reversed())
                .thenComparing(Comparator.comparingInt(Unranked::visitors).reversed())
                .thenComparing(Comparator.comparingLong(Unranked::totalLikes).reversed())
                .thenComparing(entry -> entry.city.getUuid().toString()));
        List<CityPopularityEntry> ranked = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            Unranked entry = entries.get(i);
            ranked.add(new CityPopularityEntry(entry.city, i + 1, entry.score,
                    entry.visitors, entry.likes, entry.totalLikes));
        }
        popular = List.copyOf(ranked);
    }

    private Metrics metrics(CitySocialState state) {
        Set<String> days = Set.copyOf(config.clock().recentDays(now.getAsLong(), config.hotWindowDays()));
        CitySocialState.Metrics raw = state.metrics(days);
        long score = Math.addExact(Math.multiplyExact(raw.visitors(), config.visitorWeight()),
                Math.multiplyExact(raw.likes(), config.likeWeight()));
        return new Metrics(raw.visitors(), raw.likes(), score);
    }

    private int votesUsed(UUID playerId) { return weeklyVotes.getOrDefault(playerId, Set.of()).size(); }
    private long totalOrZero(CityState city) {
        if (city == null || store.error(city.getUuid()) != null) return 0L;
        return store.state(city).totalLikes();
    }
    private boolean isPublicReady(CityState city) {
        return city != null && city.getLifecycleState() == CityLifecycleState.ACTIVE
                && city.getWorldState() == CityWorldState.READY
                && city.getWorldVisibility() == WorldVisibility.PUBLIC && !city.isDeleted();
    }

    private <T> CompletionStage<T> onMain(Callable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable task = () -> { try { future.complete(callable.call()); }
        catch (Throwable error) { future.completeExceptionally(error); } };
        if (Bukkit.isPrimaryThread()) task.run(); else Bukkit.getScheduler().runTask(plugin, task);
        return future;
    }

    private void requireMain() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("社交操作必须在主线程执行");
    }

    private record Metrics(int visitors, int likes, long score) { }
    private record Unranked(CityState city, long score, int visitors, int likes, long totalLikes) { }
}
