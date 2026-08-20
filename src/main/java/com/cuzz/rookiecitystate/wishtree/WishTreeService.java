package com.cuzz.rookiecitystate.wishtree;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.api.event.WishCompletedEvent;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.CityStateBank;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.world.CityLifecycleState;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.DoubleSupplier;

public final class WishTreeService {
    private static final long WEEKLY_JOIN_AGE_MILLIS = 72L * 60L * 60L * 1000L;
    private final RookieCityState plugin;
    private final WishTreeStore store;
    private final WishRewardService rewardService;
    private final WishTreeClock clock;
    private final DoubleSupplier random;
    private volatile WishRewardCatalog catalog;
    private volatile WishTreeVisualService visuals;

    public WishTreeService(RookieCityState plugin, YamlConfiguration rewards) {
        this(plugin, rewards, Math::random);
    }

    WishTreeService(RookieCityState plugin, YamlConfiguration rewards, DoubleSupplier random) {
        this.plugin = plugin;
        this.catalog = WishRewardCatalog.load(rewards);
        this.random = random;
        this.store = new WishTreeStore(plugin);
        this.rewardService = new WishRewardService(plugin);
        this.clock = new WishTreeClock(ZoneId.of(MainSettings.getWishTreeTimezone()), MainSettings.getWishTreeResetHour());
    }

    public void setVisualService(WishTreeVisualService visuals) { this.visuals = visuals; }
    public void reloadCatalog(YamlConfiguration rewards) {
        WishRewardCatalog next = WishRewardCatalog.load(rewards);
        this.catalog = next;
        for (CityStatePlayer player : plugin.getCityStatePlayerManager().getLoadedCityStatePlayers()) {
            synchronized (player) {
                PlayerWishData personal = new PlayerWishData(player);
                String target = personal.targetId();
                if (target != null && next.get(target) == null) {
                    personal.clearTarget();
                    personal.save();
                }
            }
        }
    }
    public WishRewardCatalog getCatalog() { return catalog; }
    public WishTreeStore getStore() { return store; }

    public WishTreeView getView(Player player, CityState cityState) {
        CityStatePlayer data = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        WishTreeState tree = store.get(cityState);
        long now = System.currentTimeMillis();
        tree.ensureWeek(clock.week(now));
        PlayerWishData personal = new PlayerWishData(data);
        personal.ensureDay(clock.day(now));
        WishRewardDefinition selected = catalog.get(personal.targetId());
        if (personal.targetId() != null && (selected == null || !selected.targetable()
                || selected.minimumTreeLevel() > tree.getLevel())) {
            personal.clearTarget();
        }
        personal.save();
        settleCityRewards(cityState, tree);
        return new WishTreeView(cityState.getUuid(), tree.getLevel(), tree.getExperience(), tree.getVisualLevel(),
                tree.getVisualState(), tree.getWeek(), tree.getWeeklyGrowth(), tree.getWeeklyTarget(),
                tree.getUnlockedMilestones(), personal.stones(), Math.max(0, 1 - personal.freeUsed()),
                Math.max(0, 5 - personal.paidUsed()), personal.targetId(), personal.pity(WishQuality.RARE),
                personal.pity(WishQuality.EPIC), tree.wateredOn(player.getUniqueId(), clock.day(now)),
                personal.mailboxSize());
    }

    public TargetResult selectTarget(Player player, String rewardId) {
        CityState city = plugin.getCityStateManager().getCityStateByMember(player.getUniqueId());
        if (!canUse(player, city)) return TargetResult.failed("当前不能使用城邦许愿树");
        WishRewardDefinition reward = catalog.get(rewardId);
        WishTreeState tree = store.get(city);
        if (reward == null || !reward.targetable()) return TargetResult.failed("心愿目标不存在");
        if (reward.minimumTreeLevel() > tree.getLevel()) return TargetResult.failed("城邦树等级不足");
        CityStatePlayer data = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        synchronized (data) {
            PlayerWishData personal = new PlayerWishData(data);
            personal.target(reward);
            personal.save();
        }
        return TargetResult.ok(rewardId);
    }

    public CompletionStage<WishResult> wish(Player player, CityState cityState) {
        return onMain(() -> doWish(player, cityState));
    }

    private WishResult doWish(Player player, CityState cityState) {
        if (!canUse(player, cityState)) return WishResult.failed("当前不能使用城邦许愿树");
        CityStatePlayer data = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        WishResult result;
        synchronized (data) {
            PlayerWishData personal = new PlayerWishData(data);
            personal.ensureDay(clock.day(System.currentTimeMillis()));
            if (personal.mailboxRemaining() < 2) return WishResult.failed("待领取奖励箱至少需要两个空位");
            WishRewardDefinition target = catalog.get(personal.targetId());
            WishTreeState tree = store.get(cityState);
            if (target == null || !target.targetable()) {
                personal.clearTarget();
                personal.save();
                return WishResult.failed("请先选择心愿目标");
            }
            if (target.minimumTreeLevel() > tree.getLevel()) return WishResult.failed("当前树等级尚未解锁该心愿");
            try {
                personal.consumeWish();
                WishRewardDefinition base = chooseBase(tree.getLevel());
                List<UUID> claims = new ArrayList<>();
                claims.add(personal.enqueue(base, "WISH_BASE", cityState.getUuid()));
                WishQuality quality = target.quality();
                int nextPity = personal.pity(quality) + 1;
                boolean targetAwarded = random.getAsDouble() < catalog.earlyChance(quality)
                        || nextPity >= catalog.pityLimit(quality);
                if (targetAwarded) {
                    claims.add(personal.enqueue(target, "WISH_TARGET", cityState.getUuid()));
                    personal.pity(quality, 0);
                } else personal.pity(quality, nextPity);
                personal.save();
                result = WishResult.ok(claims, targetAwarded);
            } catch (RuntimeException error) {
                data.load();
                return WishResult.failed(error.getMessage());
            }
        }
        Bukkit.getPluginManager().callEvent(new WishCompletedEvent(cityState, data, result));
        return result;
    }

    public CompletionStage<WaterResult> water(Player player, CityState cityState) {
        return onMain(() -> {
            if (!canUse(player, cityState)) return WaterResult.failed("当前不能给城邦许愿树浇水");
            WishTreeState tree = store.get(cityState);
            long now = System.currentTimeMillis();
            WishTreeState.WeekMutation mutation = tree.water(player.getUniqueId(), clock.day(now), clock.week(now));
            if (!mutation.changed()) return WaterResult.failed("今天已经浇过水了");
            settleCityRewards(cityState, tree);
            if (mutation.desiredLevel() > tree.getLevel() && visuals != null) visuals.requestLevel(cityState, mutation.desiredLevel());
            return WaterResult.ok(mutation.growth(), mutation.target(), mutation.newlyUnlocked());
        });
    }

    public CompletionStage<ClaimResult> claim(Player player, UUID claimId) {
        return onMain(() -> {
            CityStatePlayer data = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
            synchronized (data) { return rewardService.claim(player, data, new PlayerWishData(data), claimId); }
        });
    }

    public CompletionStage<ClaimResult> claimWeekly(Player player, CityState cityState, int milestone) {
        return onMain(() -> {
            if (!canUse(player, cityState)) return ClaimResult.failed("当前不能领取周奖励", null);
            if (!WishTreeState.MILESTONES.contains(milestone)) return ClaimResult.failed("周奖励档位无效", null);
            WishTreeState tree = store.get(cityState);
            String week = clock.week(System.currentTimeMillis());
            tree.ensureWeek(week);
            if (!tree.getUnlockedMilestones().contains(milestone)) return ClaimResult.failed("该档周奖励尚未解锁", null);
            CityStateMember member = cityState.getMember(player.getUniqueId());
            if (member == null || System.currentTimeMillis() - member.getJoinTime() < WEEKLY_JOIN_AGE_MILLIS
                    || tree.getWaterCount(player.getUniqueId()) < 3) {
                return ClaimResult.failed("需要入邦满72小时且本周浇水至少3次", null);
            }
            CityStatePlayer data = member.getCityStatePlayer();
            synchronized (data) {
                PlayerWishData personal = new PlayerWishData(data);
                if (personal.weeklyClaimed(week, cityState.getUuid(), milestone)) {
                    return ClaimResult.failed("该档周奖励已经领取", WishClaimState.CLAIMED);
                }
                WishRewardCatalog.WeeklyReward weekly = catalog.weekly(milestone);
                if (personal.mailboxRemaining() < weekly.rewardIds().size()) {
                    return ClaimResult.failed("待领取奖励箱空间不足", WishClaimState.READY);
                }
                for (String rewardId : weekly.rewardIds()) {
                    personal.enqueue(catalog.get(rewardId), "WEEKLY_" + milestone, cityState.getUuid());
                }
                personal.markWeeklyClaimed(week, cityState.getUuid(), milestone);
                personal.save();
                return ClaimResult.ok();
            }
        });
    }

    public boolean grantSignStone(CityStatePlayer player) {
        synchronized (player) {
            PlayerWishData personal = new PlayerWishData(player);
            boolean granted = personal.grantSignStoneOnce(clock.day(System.currentTimeMillis()));
            if (granted) personal.save();
            return granted;
        }
    }

    public void grantStones(CityStatePlayer player, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("数量必须大于 0");
        synchronized (player) {
            PlayerWishData personal = new PlayerWishData(player);
            personal.addStones(amount);
            personal.save();
        }
    }

    public List<UUID> grantReward(CityStatePlayer player, String rewardId, int count, UUID cityId) {
        if (count < 1 || count > 100) throw new IllegalArgumentException("补发数量必须为 1-100");
        WishRewardDefinition reward = catalog.get(rewardId);
        if (reward == null) throw new IllegalArgumentException("奖励不存在: " + rewardId);
        synchronized (player) {
            PlayerWishData personal = new PlayerWishData(player);
            if (personal.mailboxRemaining() < count) throw new IllegalStateException("待领取奖励箱空间不足");
            List<UUID> result = new ArrayList<>();
            for (int i = 0; i < count; i++) result.add(personal.enqueue(reward, "ADMIN_GRANT", cityId));
            personal.save();
            return result;
        }
    }

    public List<WishRewardClaim> mailbox(CityStatePlayer player) {
        synchronized (player) { return new PlayerWishData(player).mailbox(); }
    }

    public void resolveClaim(CityStatePlayer player, UUID claimId, boolean delivered) {
        synchronized (player) { rewardService.resolve(player, claimId, delivered); }
    }

    public void resetDaily(CityStatePlayer player) {
        synchronized (player) {
            backupPlayer(player);
            PlayerWishData personal = new PlayerWishData(player);
            personal.resetDaily(clock.day(System.currentTimeMillis()));
            personal.save();
        }
    }

    public void resetPity(CityStatePlayer player, Set<WishQuality> qualities) {
        synchronized (player) {
            backupPlayer(player);
            PlayerWishData personal = new PlayerWishData(player);
            qualities.forEach(personal::resetPity);
            personal.save();
        }
    }

    public void resetWeekly(CityState cityState) {
        WishTreeState state = store.get(cityState);
        store.backup(state, String.valueOf(System.currentTimeMillis()));
        state.resetWeek(clock.week(System.currentTimeMillis()));
    }

    public CompletionStage<Void> resetLevel(CityState cityState, int level) {
        WishTreeState state = store.get(cityState);
        store.backup(state, String.valueOf(System.currentTimeMillis()));
        state.resetLevel(level);
        return visuals == null ? CompletableFuture.failedFuture(new IllegalStateException("FAWE 视觉服务不可用"))
                : visuals.requestLevel(cityState, level);
    }

    public void archive(CityState cityState) { store.archive(cityState); }

    public void memberLeft(CityState cityState, UUID playerId) {
        store.get(cityState).removeParticipant(playerId);
    }

    public WishTreeAdminView getAdminView(CityState cityState) {
        WishTreeState state = store.get(cityState);
        state.ensureWeek(clock.week(System.currentTimeMillis()));
        return new WishTreeAdminView(cityState.getUuid(), state.getLevel(), state.getExperience(),
                state.getVisualLevel(), state.getVisualState(), state.getLastError(), state.getWeek(),
                state.getWeeklyGrowth(), state.getWeeklyTarget(), state.getParticipantCount(),
                state.getUnlockedMilestones());
    }

    public WishPlayerAdminView getAdminView(CityStatePlayer player) {
        synchronized (player) {
            PlayerWishData personal = new PlayerWishData(player);
            personal.ensureDay(clock.day(System.currentTimeMillis()));
            List<WishRewardClaim> mailbox = personal.mailbox();
            personal.save();
            return new WishPlayerAdminView(personal.stones(), personal.targetId(), personal.pity(WishQuality.RARE),
                    personal.pity(WishQuality.EPIC), personal.freeUsed(), personal.paidUsed(), mailbox.size(),
                    (int) mailbox.stream().filter(claim -> claim.state() == WishClaimState.AMBIGUOUS).count());
        }
    }

    public CompletionStage<Void> retryVisual(CityState cityState) {
        if (visuals == null) return CompletableFuture.failedFuture(new IllegalStateException("FAWE 视觉服务不可用"));
        WishTreeState state = store.get(cityState);
        return visuals.requestLevel(cityState, Math.max(state.getLevel(), state.getPendingLevel()));
    }

    private void backupPlayer(CityStatePlayer player) {
        File source = player.getDataFile();
        File backup = new File(source.getParentFile(), source.getName() + ".bak.wishtree." + System.currentTimeMillis());
        com.cuzz.rookiecitystate.internal.io.YamlFiles.save(player.getYaml(), backup);
    }

    private void settleCityRewards(CityState city, WishTreeState tree) {
        for (int milestone : tree.getUnlockedMilestones()) {
            if (tree.cityRewardPaid(milestone)) continue;
            WishRewardCatalog.WeeklyReward weekly = catalog.weekly(milestone);
            if (weekly != null && weekly.cityGmoney() > 0D) {
                String creditKey = "wish-tree:" + tree.getWeek() + ":" + milestone;
                city.getCityStateBank().creditOnce(creditKey, CityStateBank.BalanceType.GMONEY,
                        java.math.BigDecimal.valueOf(weekly.cityGmoney()));
            }
            tree.markCityRewardPaid(milestone);
        }
    }

    private WishRewardDefinition chooseBase(int level) {
        List<WishRewardDefinition> available = catalog.baseRewards().stream()
                .filter(reward -> reward.minimumTreeLevel() <= level).toList();
        double total = available.stream().mapToDouble(WishRewardDefinition::weight).sum();
        if (total <= 0D) throw new IllegalStateException("当前等级没有可用基础奖励");
        double needle = random.getAsDouble() * total;
        for (WishRewardDefinition reward : available) {
            needle -= reward.weight();
            if (needle < 0D) return reward;
        }
        return available.getLast();
    }

    private boolean canUse(Player player, CityState cityState) {
        return player != null && player.isOnline() && cityState != null && cityState.isValid()
                && cityState.getLifecycleState() == CityLifecycleState.ACTIVE && cityState.isWorldReady()
                && cityState.isMember(player.getUniqueId());
    }

    private <T> CompletionStage<T> onMain(Callable<T> callable) {
        if (Bukkit.isPrimaryThread()) {
            try { return CompletableFuture.completedFuture(callable.call()); }
            catch (Throwable error) { return CompletableFuture.failedFuture(error); }
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try { future.complete(callable.call()); } catch (Throwable error) { future.completeExceptionally(error); }
        });
        return future;
    }
}
