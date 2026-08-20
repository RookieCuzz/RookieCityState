package com.cuzz.rookiecitystate.guardian;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.api.event.GuardianDailyCompletedEvent;
import com.cuzz.rookiecitystate.api.event.GuardianFedEvent;
import com.cuzz.rookiecitystate.api.event.GuardianLevelChangedEvent;
import com.cuzz.rookiecitystate.api.event.GuardianSpeciesSelectedEvent;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.world.CityLifecycleState;
import com.cuzz.rookiecitystate.world.CityWorldState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.random.RandomGenerator;
import java.util.function.LongSupplier;

public final class GuardianBeastService {
    private final RookieCityState plugin;
    private final GuardianBeastStore store;
    private final RandomGenerator random;
    private final LongSupplier now;
    private final Set<UUID> inFlight = Collections.synchronizedSet(new HashSet<>());
    private volatile GuardianBeastConfig config;
    private volatile GuardianVisualService visuals = GuardianVisualService.unavailable("ModelEngine 模型尚未就绪");

    public GuardianBeastService(RookieCityState plugin, GuardianBeastConfig config) {
        this(plugin, config, new SplittableRandom(), System::currentTimeMillis);
    }

    GuardianBeastService(RookieCityState plugin, GuardianBeastConfig config, RandomGenerator random) {
        this(plugin, config, random, System::currentTimeMillis);
    }

    GuardianBeastService(RookieCityState plugin, GuardianBeastConfig config, RandomGenerator random, LongSupplier now) {
        this.plugin = plugin;
        this.config = config;
        this.random = random;
        this.now = now;
        this.store = new GuardianBeastStore(plugin);
    }

    public void setVisualService(GuardianVisualService visuals) { this.visuals = visuals; }
    public void reloadConfig(GuardianBeastConfig next) {
        this.config = next;
        visuals.reconcileLoadedWorlds().toCompletableFuture().join();
    }
    public GuardianBeastConfig getConfig() { return config; }
    public GuardianBeastStore getStore() { return store; }
    public boolean isAvailable() { return visuals.isAvailable(); }
    public String unavailableReason() { return visuals.unavailableReason(); }

    public GuardianBeastView getView(Player player, CityState cityState) {
        GuardianBeastState state = state(cityState);
        String day = config.day(now.getAsLong());
        state.ensureDay(day, config, random);
        CityStatePlayer cityPlayer = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        PlayerGuardianData personal = new PlayerGuardianData(cityPlayer);
        synchronized (cityPlayer) {
            personal.ensureDay(day);
            personal.save();
        }
        GuardianSpecies species = state.species();
        String name = species == null ? "公共灵兽蛋" : config.species(species).displayName();
        return new GuardianBeastView(cityState.getUuid(), species, name, config.form(state.completedDays()),
                config.level(state.completedDays()), state.completedDays(), state.day(), state.favorites(),
                state.fullness(), state.target(), state.completedToday(), personal.feeds(),
                Math.max(0, config.maxFeeds() - personal.feeds()), personal.available(), personal.lifetime(),
                state.dailyContributions(), visuals.isAvailable(), state.visualError() == null
                ? visuals.unavailableReason() : state.visualError());
    }

    public SpeciesSelectionResult selectSpecies(Player player, CityState cityState, GuardianSpecies species) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("选择灵兽必须在主线程执行");
        if (!visuals.isAvailable()) return SpeciesSelectionResult.failed(
                SpeciesSelectionResult.Status.MODULE_UNAVAILABLE, visuals.unavailableReason());
        CityStatePlayer cityPlayer = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        if (!usable(cityState)) return SpeciesSelectionResult.failed(SpeciesSelectionResult.Status.CITY_UNAVAILABLE, "城邦当前不可用");
        if (!cityState.isOwner(cityPlayer)) return SpeciesSelectionResult.failed(SpeciesSelectionResult.Status.NOT_OWNER, "只有会长可以选择灵兽");
        GuardianBeastState state = state(cityState);
        synchronized (state) {
            if (state.species() != null) return SpeciesSelectionResult.failed(SpeciesSelectionResult.Status.ALREADY_SELECTED, "灵兽种类已永久锁定");
            String before = state.snapshot();
            try {
                state.select(species, player.getUniqueId(), now.getAsLong());
                state.save();
            } catch (RuntimeException error) {
                state.restore(before);
                return SpeciesSelectionResult.failed(SpeciesSelectionResult.Status.SAVE_FAILED, "灵兽选择保存失败");
            }
        }
        Bukkit.getPluginManager().callEvent(new GuardianSpeciesSelectedEvent(cityState, cityPlayer, species));
        visuals.ensureVisual(cityState).exceptionally(error -> { state.visualFailed(error); return null; });
        return new SpeciesSelectionResult(SpeciesSelectionResult.Status.SUCCESS, "已选择 " + config.species(species).displayName());
    }

    public CompletionStage<FeedResult> feed(Player player, CityState cityState, EquipmentSlot hand) {
        return onMain(() -> doFeed(player, cityState, hand));
    }

    private FeedResult doFeed(Player player, CityState cityState, EquipmentSlot hand) {
        if (hand != EquipmentSlot.HAND) return FeedResult.failed(FeedResult.Status.INVALID_HAND, "已忽略副手交互");
        if (!visuals.isAvailable()) return FeedResult.failed(FeedResult.Status.MODULE_UNAVAILABLE, visuals.unavailableReason());
        if (!usable(cityState)) return FeedResult.failed(FeedResult.Status.CITY_UNAVAILABLE, "城邦当前不可用");
        if (cityState.getMember(player.getUniqueId()) == null
                || plugin.getCityStateManager().getCityStateByMember(player.getUniqueId()) != cityState) {
            return FeedResult.failed(FeedResult.Status.NOT_MEMBER, "只有当前城邦成员可以喂养");
        }
        if (player.getWorld() == null || !player.getWorld().getName().equals(cityState.getWorldName())) {
            return FeedResult.failed(FeedResult.Status.CITY_UNAVAILABLE, "请在本城邦灵兽旁喂养");
        }
        GuardianBeastState state = state(cityState);
        if (state.species() == null) return FeedResult.failed(FeedResult.Status.SPECIES_NOT_SELECTED, "会长尚未选择灵兽种类");
        String feedDay = config.day(now.getAsLong());
        try { synchronized (state) { state.ensureDay(feedDay, config, random); } }
        catch (RuntimeException error) {
            return FeedResult.failed(FeedResult.Status.SAVE_FAILED, "每日灵兽状态保存失败，未消耗食物");
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        GuardianFood food = config.food(held.getType());
        if (food == null) food = state.snapshottedFavorite(held.getType());
        if (food == null || held.getAmount() < 1) return FeedResult.failed(FeedResult.Status.INVALID_FOOD, "主手物品不是有效食物");
        if (!inFlight.add(player.getUniqueId())) return FeedResult.failed(FeedResult.Status.BUSY, "上一次喂养仍在处理中");

        CityStatePlayer cityPlayer = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        try {
            synchronized (state) {
                synchronized (cityPlayer) {
                    String day = feedDay;
                    try {
                        state.ensureDay(day, config, random);
                    } catch (RuntimeException error) {
                        return FeedResult.failed(FeedResult.Status.SAVE_FAILED,
                                "每日灵兽状态保存失败，未消耗食物");
                    }
                    PlayerGuardianData personal = new PlayerGuardianData(cityPlayer);
                    personal.ensureDay(day);
                    personal.save();
                    GuardianFeedOperation pending = GuardianFeedOperation.load(plugin, player.getUniqueId());
                    if (pending != null) {
                        GuardianFeedOperation.Recovery recovery = pending.recover(player, state, personal);
                        if (recovery == GuardianFeedOperation.Recovery.RECONCILIATION_REQUIRED) {
                            return FeedResult.failed(FeedResult.Status.BUSY,
                                    "上次喂养结果无法自动确认，已隔离等待管理员对账");
                        }
                        return FeedResult.failed(FeedResult.Status.BUSY,
                                recovery == GuardianFeedOperation.Recovery.COMPLETED
                                        ? "上次喂养已自动完成，请重新点击" : "上次未完成喂养已安全回滚，请重新点击");
                    }
                    if (personal.feeds() >= config.maxFeeds()) {
                        return FeedResult.failed(FeedResult.Status.DAILY_LIMIT, "今日全局喂养次数已用完");
                    }
                    String stateBefore = state.snapshot();
                    String playerBefore = personal.snapshot();
                    ItemStack itemBefore = held.clone();
                    int oldLevel = config.level(state.completedDays());
                    GuardianForm oldForm = config.form(state.completedDays());
                    GuardianFeedOperation operation;
                    try { operation = GuardianFeedOperation.create(plugin, player, cityState.getUuid(), state, personal, itemBefore); }
                    catch (RuntimeException error) {
                        return FeedResult.failed(FeedResult.Status.SAVE_FAILED, "无法创建喂养事务日志，未消耗食物");
                    }
                    try {
                        GuardianFeedMutation mutation = state.applyFeed(player.getUniqueId(), food,
                                config.favoriteMultiplier(), now.getAsLong());
                        personal.applyFeed(mutation.contribution());
                        ItemStack itemAfter = itemBefore.clone();
                        itemAfter.setAmount(itemAfter.getAmount() - 1);
                        if (itemAfter.getAmount() <= 0) itemAfter = null;
                        operation.after(state, personal, itemAfter);
                        player.getInventory().setItemInMainHand(itemAfter);
                        operation.phase("INVENTORY_APPLIED");
                        personal.save();
                        operation.phase("PLAYER_SAVED");
                        state.save();
                        operation.phase("STATE_SAVED");
                        operation.complete();
                        int newLevel = config.level(state.completedDays());
                        GuardianForm newForm = config.form(state.completedDays());
                        FeedResult result = new FeedResult(FeedResult.Status.SUCCESS, mutation.nourishment(),
                                mutation.contribution(), mutation.favorite(), mutation.dailyCompleted(), oldLevel,
                                newLevel, mutation.favorite() ? "灵兽很喜欢这份食物！" : "喂养成功");
                        Bukkit.getPluginManager().callEvent(new GuardianFedEvent(cityState, cityPlayer, result));
                        if (mutation.dailyCompleted()) Bukkit.getPluginManager().callEvent(
                                new GuardianDailyCompletedEvent(cityState, cityPlayer, state.completedDays()));
                        if (oldLevel != newLevel) {
                            Bukkit.getPluginManager().callEvent(new GuardianLevelChangedEvent(cityState, oldLevel, newLevel));
                            visuals.updateVisual(cityState, oldForm, newForm)
                                    .exceptionally(error -> { state.visualFailed(error); return null; });
                        }
                        return result;
                    } catch (RuntimeException error) {
                        player.getInventory().setItemInMainHand(itemBefore);
                        state.restore(stateBefore);
                        personal.restore(playerBefore);
                        boolean restored = true;
                        try { personal.save(); } catch (RuntimeException ignored) { restored = false; }
                        try { state.save(); } catch (RuntimeException ignored) { restored = false; }
                        try {
                            if (restored) operation.complete(); else operation.reconciliation(error);
                        } catch (RuntimeException logError) { error.addSuppressed(logError); }
                        return FeedResult.failed(FeedResult.Status.SAVE_FAILED, "喂养保存失败，食物与数据已回滚");
                    }
                }
            }
        } finally {
            inFlight.remove(player.getUniqueId());
        }
    }

    public CompletionStage<Void> ensureVisual(CityState cityState) {
        CompletionStage<Void> stage = visuals.ensureVisual(cityState);
        stage.whenComplete((ignored, error) -> { if (error != null) state(cityState).visualFailed(error); });
        return stage;
    }
    public CompletionStage<Void> retryVisual(CityState cityState) {
        CompletionStage<Void> stage = visuals.retry(cityState);
        stage.whenComplete((ignored, error) -> { if (error != null) state(cityState).visualFailed(error); });
        return stage;
    }
    public CompletionStage<Void> playVisualAction(CityState cityState, java.util.List<GuardianAnimationStep> steps) {
        return visuals.playAction(cityState, steps);
    }
    public GuardianBeastState state(CityState cityState) { return store.get(cityState, config.modelRevision()); }
    public void archive(CityState cityState) { store.archive(cityState); }

    public void resetDaily(CityState cityState) {
        GuardianBeastState state = state(cityState);
        store.backup(state, "daily-" + Instant.now().toEpochMilli());
        state.resetDaily(config.day(now.getAsLong()), config, random);
    }

    public void resetSpecies(CityState cityState) {
        GuardianBeastState state = state(cityState);
        store.backup(state, "species-" + Instant.now().toEpochMilli());
        synchronized (state) { state.resetSpecies(); state.save(); }
        visuals.retry(cityState).exceptionally(error -> { state.visualFailed(error); return null; });
    }

    public void setDays(CityState cityState, int days) {
        GuardianBeastState state = state(cityState);
        store.backup(state, "days-" + Instant.now().toEpochMilli());
        GuardianForm oldForm = config.form(state.completedDays());
        synchronized (state) { state.setCompletedDays(days); state.save(); }
        visuals.updateVisual(cityState, oldForm, config.form(days))
                .exceptionally(error -> { state.visualFailed(error); return null; });
    }

    public void resetPlayerDaily(CityStatePlayer player) {
        synchronized (player) { PlayerGuardianData data = new PlayerGuardianData(player);
            data.resetDaily(config.day(now.getAsLong())); data.save(); }
    }

    public void grantContribution(CityStatePlayer player, long amount) {
        synchronized (player) { PlayerGuardianData data = new PlayerGuardianData(player); data.grant(amount); data.save(); }
    }

    public long[] playerContribution(CityStatePlayer player) {
        synchronized (player) {
            PlayerGuardianData data = new PlayerGuardianData(player);
            String today = config.day(now.getAsLong());
            if (!today.equals(player.getYaml().getString("guardian_beast.daily.cycle"))) {
                data.ensureDay(today);
                data.save();
            }
            return new long[]{data.available(), data.lifetime(), data.feeds()};
        }
    }

    private boolean usable(CityState cityState) {
        return cityState != null && cityState.getLifecycleState() == CityLifecycleState.ACTIVE
                && cityState.getWorldState() == CityWorldState.READY && !cityState.isDeleted();
    }

    private <T> CompletionStage<T> onMain(Callable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable task = () -> {
            try { future.complete(callable.call()); }
            catch (Throwable error) { future.completeExceptionally(error); }
        };
        if (Bukkit.isPrimaryThread()) task.run(); else Bukkit.getScheduler().runTask(plugin, task);
        return future;
    }
}
