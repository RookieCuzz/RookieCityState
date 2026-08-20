package com.cuzz.rookiecitystate.guardian.shop;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.api.event.GuardianCosmeticEquippedEvent;
import com.cuzz.rookiecitystate.api.event.GuardianShopPurchasedEvent;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.guardian.GuardianBeastService;
import com.cuzz.rookiecitystate.guardian.GuardianForm;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.wishtree.WishQuality;
import com.cuzz.rookiecitystate.wishtree.WishRewardAction;
import com.cuzz.rookiecitystate.wishtree.WishRewardDefinition;
import com.cuzz.rookiecitystate.wishtree.WishRewardInboxMutation;
import com.cuzz.rookiecitystate.wishtree.WishRewardType;
import com.cuzz.rookiecitystate.world.CityLifecycleState;
import com.cuzz.rookiecitystate.world.CityWorldState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
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

public final class GuardianContributionShopService {
    private final RookieCityState plugin;
    private final GuardianBeastService guardian;
    private final GuardianShopRotationStore rotations;
    private final LongSupplier now;
    private final Set<UUID> purchasesInFlight = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> actionsInFlight = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, Long> actionCooldowns = new HashMap<>();
    private final BukkitTask particleTask;
    private volatile GuardianShopConfig config;
    private long particleTicks;
    private int particleCursor;

    public GuardianContributionShopService(RookieCityState plugin, GuardianBeastService guardian,
                                           GuardianShopConfig config) {
        this(plugin, guardian, config, System::currentTimeMillis);
    }

    GuardianContributionShopService(RookieCityState plugin, GuardianBeastService guardian,
                                    GuardianShopConfig config, LongSupplier now) {
        this.plugin = plugin;
        this.guardian = guardian;
        this.config = config;
        this.now = now;
        this.rotations = new GuardianShopRotationStore(plugin);
        this.particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::particleTick, 1L, 1L);
    }

    public GuardianShopConfig getConfig() { return config; }
    public void reloadConfig(GuardianShopConfig next) { this.config = next; }
    public GuardianShopRotation getCurrentRotation() { return rotations.current(config, now.getAsLong()); }

    public GuardianShopView getView(Player player, CityState cityState) {
        if (!usable(player, cityState)) throw new IllegalStateException("当前不能使用灵兽贡献商店");
        GuardianShopRotation rotation = getCurrentRotation();
        CityStatePlayer data = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        synchronized (data) {
            GuardianShopPlayerData personal = new GuardianShopPlayerData(data);
            return new GuardianShopView(rotation.cycle(), config.clock().nextWeekStart(now.getAsLong()),
                    personal.available(), personal.lifetime(), rotation.products(), personal.ownedIds(),
                    personal.equipped(), personal.purchaseCounts(rotation.cycle(), rotation.products()));
        }
    }

    public CompletionStage<GuardianPurchaseResult> purchase(Player player, CityState cityState, String productId) {
        return onMain(() -> doPurchase(player, cityState, productId));
    }

    private GuardianPurchaseResult doPurchase(Player player, CityState cityState, String productId) {
        if (!usable(player, cityState)) return GuardianPurchaseResult.failed(
                GuardianPurchaseResult.Status.NOT_MEMBER, "只有当前城邦成员可以购买");
        GuardianShopRotation rotation = getCurrentRotation();
        GuardianShopProduct product = rotation.product(productId);
        if (product == null) return GuardianPurchaseResult.failed(
                GuardianPurchaseResult.Status.PRODUCT_UNAVAILABLE, "商品不在本周轮换中");
        int level = guardian.getConfig().level(guardian.state(cityState).completedDays());
        if (level < product.minimumGuardianLevel()) return GuardianPurchaseResult.failed(
                GuardianPurchaseResult.Status.LEVEL_LOCKED, "当前城邦灵兽等级不足");
        if (!purchasesInFlight.add(player.getUniqueId())) return GuardianPurchaseResult.failed(
                GuardianPurchaseResult.Status.BUSY, "上一次购买仍在处理中");
        CityStatePlayer data = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        try {
            synchronized (data) {
                GuardianShopPlayerData personal = new GuardianShopPlayerData(data);
                if (product.permanent() && personal.owns(product.id())) return GuardianPurchaseResult.failed(
                        GuardianPurchaseResult.Status.ALREADY_OWNED, "该永久商品已经拥有");
                if (personal.purchaseCount(rotation.cycle(), product.id()) >= product.weeklyLimit()) {
                    return GuardianPurchaseResult.failed(GuardianPurchaseResult.Status.WEEKLY_LIMIT, "本周购买次数已用完");
                }
                if (personal.available() < product.price()) return GuardianPurchaseResult.failed(
                        GuardianPurchaseResult.Status.INSUFFICIENT_CONTRIBUTION, "可用贡献不足");
                if (!product.permanent() && WishRewardInboxMutation.remaining(data) < 1) {
                    return GuardianPurchaseResult.failed(GuardianPurchaseResult.Status.INBOX_FULL, "待领取奖励箱已满");
                }
                String before = personal.snapshot();
                UUID claimId = null;
                try {
                    personal.spend(product.price());
                    if (product.permanent()) personal.own(product, now.getAsLong());
                    else claimId = WishRewardInboxMutation.enqueue(data, reward(product),
                            "GUARDIAN_SHOP:" + product.id() + ":" + rotation.cycle(), cityState.getUuid());
                    personal.recordPurchase(rotation.cycle(), product.id());
                    personal.save();
                } catch (RuntimeException error) {
                    personal.restore(before);
                    try { personal.save(); } catch (RuntimeException ignored) { }
                    return GuardianPurchaseResult.failed(GuardianPurchaseResult.Status.SAVE_FAILED,
                            "购买保存失败，贡献与商品已回滚");
                }
                GuardianPurchaseResult result = new GuardianPurchaseResult(GuardianPurchaseResult.Status.SUCCESS,
                        "购买成功", personal.available(), claimId);
                Bukkit.getPluginManager().callEvent(new GuardianShopPurchasedEvent(cityState, data, product, result));
                return result;
            }
        } finally {
            purchasesInFlight.remove(player.getUniqueId());
        }
    }

    public void equip(Player player, GuardianCosmeticSlot slot, String productId) {
        requireMain();
        CityState city = plugin.getCityStateManager().getCityStateByMember(player.getUniqueId());
        if (!usable(player, city)) throw new IllegalStateException("当前不能装备灵兽装扮");
        CityStatePlayer data = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        GuardianShopProduct product;
        synchronized (data) {
            GuardianShopPlayerData personal = new GuardianShopPlayerData(data);
            String before = personal.snapshot();
            try { personal.equip(slot, productId); product = personal.owned(productId); personal.save(); }
            catch (RuntimeException error) { personal.restore(before); throw error; }
        }
        Bukkit.getPluginManager().callEvent(new GuardianCosmeticEquippedEvent(data, slot, product));
    }

    public void unequip(Player player, GuardianCosmeticSlot slot) {
        requireMain();
        CityState city = plugin.getCityStateManager().getCityStateByMember(player.getUniqueId());
        if (!usable(player, city)) throw new IllegalStateException("当前不能卸下灵兽装扮");
        CityStatePlayer data = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        synchronized (data) {
            GuardianShopPlayerData personal = new GuardianShopPlayerData(data);
            String before = personal.snapshot();
            try { personal.unequip(slot); personal.save(); }
            catch (RuntimeException error) { personal.restore(before); throw error; }
        }
        Bukkit.getPluginManager().callEvent(new GuardianCosmeticEquippedEvent(data, slot, null));
    }

    public CompletionStage<GuardianActionResult> playEquippedAction(Player player, CityState cityState) {
        return onMain(() -> beginAction(player, cityState)).thenCompose(start -> {
            if (start.immediate != null) return CompletableFuture.completedFuture(start.immediate);
            CompletableFuture<GuardianActionResult> result = new CompletableFuture<>();
            try {
                guardian.playVisualAction(cityState, start.steps).whenComplete((ignored, error) ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            actionsInFlight.remove(cityState.getUuid());
                            if (error == null) result.complete(new GuardianActionResult(
                                    GuardianActionResult.Status.SUCCESS, "互动动作播放完成"));
                            else result.complete(GuardianActionResult.failed(GuardianActionResult.Status.FAILED,
                                    "动作播放失败: " + rootMessage(error)));
                        }));
            } catch (RuntimeException error) {
                actionsInFlight.remove(cityState.getUuid());
                result.complete(GuardianActionResult.failed(GuardianActionResult.Status.FAILED,
                        "动作播放失败: " + rootMessage(error)));
            }
            return result;
        });
    }

    private ActionStart beginAction(Player player, CityState cityState) {
        if (!usable(player, cityState)) return ActionStart.failed(GuardianActionResult.Status.NOT_MEMBER, "当前不能与灵兽互动");
        if (player.getWorld() == null || !player.getWorld().getName().equals(cityState.getWorldName())) {
            return ActionStart.failed(GuardianActionResult.Status.CITY_UNAVAILABLE, "请先进入自己的城邦世界");
        }
        Location anchor = new Location(player.getWorld(), guardian.getConfig().anchorX(), guardian.getConfig().anchorY(),
                guardian.getConfig().anchorZ());
        if (player.getLocation().distanceSquared(anchor) > config.actionDistance() * config.actionDistance()) {
            return ActionStart.failed(GuardianActionResult.Status.TOO_FAR, "请靠近城邦灵兽后再互动");
        }
        CityStatePlayer data = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        GuardianShopProduct action;
        synchronized (data) {
            GuardianShopPlayerData personal = new GuardianShopPlayerData(data);
            String id = personal.equipped(GuardianCosmeticSlot.ACTION);
            action = id == null ? null : personal.owned(id);
        }
        if (action == null) return ActionStart.failed(GuardianActionResult.Status.NOT_EQUIPPED, "尚未装备互动动作");
        GuardianForm form = guardian.getConfig().form(guardian.state(cityState).completedDays());
        List<com.cuzz.rookiecitystate.guardian.GuardianAnimationStep> steps = action.animation(form);
        if (steps.isEmpty()) return ActionStart.failed(GuardianActionResult.Status.FORM_LOCKED, "当前灵兽形态不能使用该动作");
        long current = now.getAsLong();
        long last = actionCooldowns.getOrDefault(cityState.getUuid(), Long.MIN_VALUE / 2);
        if (current - last < config.actionCooldownMillis()) return ActionStart.failed(
                GuardianActionResult.Status.COOLDOWN, "灵兽互动动作仍在冷却中");
        if (!actionsInFlight.add(cityState.getUuid())) return ActionStart.failed(
                GuardianActionResult.Status.BUSY, "该城邦灵兽正在播放其他动作");
        actionCooldowns.put(cityState.getUuid(), current);
        return new ActionStart(null, steps);
    }

    public GuardianShopRotation rotateNow() { requireMain(); return rotations.rotate(config, now.getAsLong()); }

    public void grantProduct(CityStatePlayer player, String productId) {
        GuardianShopProduct product = config.product(productId);
        if (product == null || !product.permanent()) throw new IllegalArgumentException("只能补发配置中的永久商品");
        synchronized (player) {
            backupPlayer(player, "grant-product");
            GuardianShopPlayerData data = new GuardianShopPlayerData(player);
            if (data.owns(productId)) throw new IllegalArgumentException("玩家已拥有该商品");
            data.own(product, now.getAsLong()); data.save();
        }
    }

    public void revokeProduct(CityStatePlayer player, String productId) {
        synchronized (player) { backupPlayer(player, "revoke-product");
            GuardianShopPlayerData data = new GuardianShopPlayerData(player); data.revoke(productId); data.save(); }
    }

    public void resetLimits(CityStatePlayer player) {
        synchronized (player) { backupPlayer(player, "reset-limits");
            GuardianShopPlayerData data = new GuardianShopPlayerData(player); data.resetLimits(); data.save(); }
    }

    public Map<String, GuardianShopProduct> owned(CityStatePlayer player) {
        synchronized (player) { return Map.copyOf(new GuardianShopPlayerData(player).owned()); }
    }

    public String activeText(CityStatePlayer player, GuardianCosmeticSlot slot) {
        CityState city = player.getCityState();
        if (city == null || !city.isValid() || city.isDeleted()) return "";
        synchronized (player) {
            GuardianShopPlayerData data = new GuardianShopPlayerData(player);
            String id = data.equipped(slot);
            GuardianShopProduct product = id == null ? null : data.owned(id);
            return product == null || product.text() == null ? ""
                    : org.bukkit.ChatColor.translateAlternateColorCodes('&', product.text());
        }
    }

    public void shutdown() { particleTask.cancel(); actionsInFlight.clear(); actionCooldowns.clear(); }

    private void particleTick() {
        GuardianShopConfig currentConfig = config;
        if (++particleTicks % currentConfig.particleIntervalTicks() != 0) return;
        List<? extends Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) { particleCursor = 0; return; }
        int attempts = Math.min(currentConfig.particleMaxEmitters(), online.size());
        for (int i = 0; i < attempts; i++) {
            Player player = online.get((particleCursor + i) % online.size());
            emitParticle(player, currentConfig);
        }
        particleCursor = (particleCursor + attempts) % online.size();
    }

    private void emitParticle(Player player, GuardianShopConfig currentConfig) {
        CityState city = plugin.getCityStateManager().getCityStateByMember(player.getUniqueId());
        if (!usable(player, city) || player.getWorld() == null || !player.getWorld().getName().equals(city.getWorldName())) return;
        CityStatePlayer data = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        GuardianShopProduct product;
        synchronized (data) {
            GuardianShopPlayerData personal = new GuardianShopPlayerData(data);
            String id = personal.equipped(GuardianCosmeticSlot.PARTICLE);
            product = id == null ? null : personal.owned(id);
        }
        if (product == null || product.particle() == null) return;
        double angle = ((particleTicks + player.getUniqueId().getLeastSignificantBits()) % 360L) * Math.PI / 180D;
        Location at = player.getLocation().add(Math.cos(angle) * currentConfig.particleRadius(),
                currentConfig.particleHeight(), Math.sin(angle) * currentConfig.particleRadius());
        try { player.getWorld().spawnParticle(product.particle(), at, currentConfig.particleCount(),
                0.08, 0.18, 0.08, currentConfig.particleSpeed()); }
        catch (RuntimeException error) { PluginLogger.warning("无法播放个人灵兽粒子 " + product.id() + ": " + error.getMessage()); }
    }

    private WishRewardDefinition reward(GuardianShopProduct product) {
        WishRewardAction action = switch (product.kind()) {
            case ITEM -> new WishRewardAction(WishRewardType.ITEM, product.rewardMaterial().name(), product.rewardAmount(), List.of());
            case MAGIC_STONE -> new WishRewardAction(WishRewardType.MAGIC_STONE, null, product.rewardAmount(), List.of());
            default -> throw new IllegalArgumentException("永久商品不能进入奖励箱");
        };
        return new WishRewardDefinition("guardian_shop_" + product.id(), product.displayName(), WishQuality.COMMON,
                false, 1, 0D, List.of(action));
    }

    private boolean usable(Player player, CityState city) {
        return player != null && city != null && city.getLifecycleState() == CityLifecycleState.ACTIVE
                && city.getWorldState() == CityWorldState.READY && !city.isDeleted()
                && city.getMember(player.getUniqueId()) != null
                && plugin.getCityStateManager().getCityStateByMember(player.getUniqueId()) == city;
    }

    private void backupPlayer(CityStatePlayer player, String action) {
        try {
            File folder = new File(plugin.getDataFolder(), "data" + File.separator + "guardian_shop"
                    + File.separator + "admin_backups" + File.separator + player.getUuid());
            Files.createDirectories(folder.toPath());
            Files.copy(player.getDataFile().toPath(), new File(folder, now.getAsLong() + "-" + action + ".yml").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) { throw new IllegalStateException("无法备份玩家灵兽商店数据", error); }
    }

    private <T> CompletionStage<T> onMain(Callable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable task = () -> { try { future.complete(callable.call()); } catch (Throwable error) { future.completeExceptionally(error); } };
        if (Bukkit.isPrimaryThread()) task.run(); else Bukkit.getScheduler().runTask(plugin, task);
        return future;
    }
    private void requireMain() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("操作必须在主线程执行"); }
    private String rootMessage(Throwable error) { while (error.getCause() != null) error = error.getCause();
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
    private record ActionStart(GuardianActionResult immediate,
                               List<com.cuzz.rookiecitystate.guardian.GuardianAnimationStep> steps) {
        static ActionStart failed(GuardianActionResult.Status status, String message) {
            return new ActionStart(GuardianActionResult.failed(status, message), List.of());
        }
    }
}
