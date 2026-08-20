package com.cuzz.rookiecitystate.world;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import com.cuzz.rookiecitystate.internal.text.LegacyText;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.placeholder.PlaceholderText;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.world.operation.CityWorldOperation;
import com.cuzz.rookiecitystate.world.operation.WorldOperationKind;
import com.cuzz.rookiecitystate.world.operation.WorldOperationStore;
import com.cuzz.rookiecitystate.wishtree.WishTreeVisualService;
import com.cuzz.rookiecitystate.guardian.GuardianVisualService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class CityWorldService {
    private static final String WORLD_PREFIX = "rcs_city_";
    private static final String RECOVERY_PREFIX = "rcs_recovery_";
    private static final String TEMPLATE_PREFIX = "rcs_template_";
    private static final String MARKER_FILE = ".rookiecitystate-world.yml";
    private static final String TEMPLATE_MARKER_FILE = ".rookiecitystate-template.yml";

    private final RookieCityState plugin;
    private final CityWorldBackend backend;
    private final CityWorldProtectionService protection;
    private final WorldOperationStore operationStore;
    private final ProtectionSyncOutbox protectionSyncOutbox;
    private final ExecutorService ioExecutor;
    private final Map<String, CompletableFuture<World>> loads = new HashMap<>();
    private final Map<String, Integer> leases = new HashMap<>();
    private final Map<String, BukkitTask> unloadTasks = new HashMap<>();
    private final Map<UUID, String> pendingEntries = new HashMap<>();
    private final Map<UUID, CompletableFuture<Void>> legacyProvisions = new HashMap<>();
    private final Map<UUID, CompletableFuture<Void>> protectionSyncs = new HashMap<>();
    private final Map<UUID, BukkitTask> protectionSyncRetries = new HashMap<>();
    private volatile boolean accepting = true;
    private volatile boolean templateReady;
    private volatile String templateError;
    private volatile WishTreeVisualService wishTreeVisualService;
    private volatile GuardianVisualService guardianVisualService;

    public CityWorldService(RookieCityState plugin, CityWorldBackend backend,
                            CityWorldProtectionService protection, WorldOperationStore operationStore) {
        this.plugin = plugin;
        this.backend = backend;
        this.protection = protection;
        this.operationStore = operationStore;
        this.protectionSyncOutbox = new ProtectionSyncOutbox(plugin);
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "RookieCityState-world-io");
            thread.setDaemon(true);
            return thread;
        };
        this.ioExecutor = Executors.newSingleThreadExecutor(factory);
    }

    public CityWorldBackend getBackend() { return backend; }
    public void setWishTreeVisualService(WishTreeVisualService service) { this.wishTreeVisualService = service; }
    public void setGuardianVisualService(GuardianVisualService service) { this.guardianVisualService = service; }
    public boolean isTemplateReady() { return templateReady; }
    public String getTemplateError() { return templateError; }

    public CompletionStage<Boolean> prepareBundledTemplate() {
        if (!MainSettings.isCityStateWorldBundledTemplateEnabled() || !backend.available()) {
            return CompletableFuture.completedFuture(false);
        }
        String name = MainSettings.getCityStateWorldTemplate();
        if (name == null || !name.matches("[A-Za-z0-9._-]+") || isManagedWorldName(name)
                || name.startsWith(TEMPLATE_PREFIX)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("内置模板目标世界名无效: " + name));
        }
        return runIo(() -> BundledCityTemplateInstaller.installIfMissing(plugin, backend.worldRoot(), name))
                .thenCompose(bundled -> {
                    if (!bundled) return CompletableFuture.completedFuture(false);
                    return onMain(() -> {
                        World template = backend.load(name);
                        if (!template.getPlayers().isEmpty()) {
                            throw new IllegalStateException("内置模板世界中仍有玩家");
                        }
                        backend.configureManagedWorld(name, template);
                        template.getWorldBorder().setCenter(0.0D, 0.0D);
                        template.getWorldBorder().setSize(MainSettings.getCityStateWorldBorderSize());
                        template.setSpawnLocation((int) Math.floor(MainSettings.getWishTreeSpawnX()),
                                (int) Math.floor(MainSettings.getWishTreeSpawnY()),
                                (int) Math.floor(MainSettings.getWishTreeSpawnZ()),
                                MainSettings.getWishTreeSpawnYaw());
                        return template;
                    }).thenCompose(template -> protection.ensureTemplateCore(
                            template, MainSettings.getCityStateWorldCoreRegion(),
                            MainSettings.getCityStateWorldCoreMinX(), MainSettings.getCityStateWorldCoreMinY(),
                            MainSettings.getCityStateWorldCoreMinZ(), MainSettings.getCityStateWorldCoreMaxX(),
                            MainSettings.getCityStateWorldCoreMaxY(), MainSettings.getCityStateWorldCoreMaxZ()
                    ).thenCompose(ignored -> onMain(() -> {
                            backend.save(template);
                            if (!backend.unload(name, true)) throw new IllegalStateException("无法卸载内置模板世界");
                            PluginLogger.info("内置城邦模板已就绪: " + name + " (Java 1.21.4, SHA-256 "
                                    + BundledCityTemplateInstaller.SHA256 + ")");
                            return true;
                    })).exceptionallyCompose(error -> onMain(() -> {
                        Throwable failure = unwrap(error);
                        if (template.getPlayers().isEmpty() && Bukkit.getWorld(name) != null) {
                            try { backend.unload(name, true); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
                        }
                        if (failure instanceof Exception exception) {
                            throw exception;
                        }
                        throw new CompletionException(failure);
                    })));
                });
    }

    public CompletionStage<Boolean> validateTemplate() {
        templateReady = false;
        if (!backend.available()) {
            templateError = "MyWorlds 后端不可用";
            return CompletableFuture.completedFuture(false);
        }
        return onMain(() -> {
            String name = MainSettings.getCityStateWorldTemplate();
            if (isManagedWorldName(name) || name.startsWith(TEMPLATE_PREFIX)) {
                throw new IllegalStateException("模板世界不能使用 RookieCityState 内部世界前缀");
            }
            if (!backend.exists(name)) throw new IllegalStateException("模板世界目录不存在: " + name);
            World template = backend.load(name);
            if (!template.getPlayers().isEmpty()) throw new IllegalStateException("模板世界中仍有玩家");
            validateTemplateWorld(template);
            protection.captureTemplate(template, MainSettings.getCityStateWorldCoreRegion());
            if (!protection.hasTemplateSnapshot()) throw new IllegalStateException("RookieRegions 模板保护不可用");
            backend.save(template);
            if (!backend.unload(name, true)) throw new IllegalStateException("无法卸载模板世界");
            String snapshotName = templateSnapshotName();
            if (backend.exists(snapshotName)) return new TemplatePreparation(snapshotName, null);
            WorldProvisionSpec snapshotSpec = new WorldProvisionSpec(UUID.randomUUID(), UUID.randomUUID(),
                    name, snapshotName, MainSettings.getCityStateWorldTemplateRevision(),
                    MainSettings.getCityStateWorldBorderSize());
            return new TemplatePreparation(snapshotName, backend.prepareCopy(snapshotSpec));
        }).thenCompose(preparation -> runIo(() -> {
            Path snapshotFolder = backend.worldFolder(preparation.snapshotName());
            if (preparation.copy() != null) {
                preparation.copy().copy();
                YamlConfiguration marker = new YamlConfiguration();
                marker.set("template", MainSettings.getCityStateWorldTemplate());
                marker.set("template_revision", MainSettings.getCityStateWorldTemplateRevision());
                marker.set("created_at", System.currentTimeMillis());
                YamlFiles.save(marker, snapshotFolder.resolve(TEMPLATE_MARKER_FILE).toFile());
            }
            Files.deleteIfExists(snapshotFolder.resolve(BundledCityTemplateInstaller.MARKER));
            verifyTemplateSnapshot(snapshotFolder);
            return preparation;
        })).thenCompose(preparation -> onMain(() -> {
            World snapshot = backend.load(preparation.snapshotName());
            if (!snapshot.getPlayers().isEmpty()) throw new IllegalStateException("模板快照中仍有玩家");
            backend.configureManagedWorld(preparation.snapshotName(), snapshot);
            validateTemplateWorld(snapshot);
            return new LoadedTemplateSnapshot(preparation, snapshot);
        })).thenCompose(loaded -> {
            CompletionStage<Void> installation = protection.installTemplateSnapshot(loaded.world());
            return installation.thenCompose(ignored -> onMain(() -> {
                protection.captureTemplate(loaded.world(), MainSettings.getCityStateWorldCoreRegion());
                if (!protection.hasTemplateSnapshot()) {
                    throw new IllegalStateException("RookieRegions 模板保护不可用");
                }
                backend.save(loaded.world());
                if (!backend.unload(loaded.preparation().snapshotName(), true)) {
                    throw new IllegalStateException("无法卸载模板快照");
                }
                templateReady = true;
                templateError = null;
                return true;
            })).exceptionallyCompose(error -> onMain(() -> {
                Throwable failure = unwrap(error);
                if (loaded.world().getPlayers().isEmpty()
                        && Bukkit.getWorld(loaded.preparation().snapshotName()) != null) {
                    try { backend.unload(loaded.preparation().snapshotName(), true); }
                    catch (Exception cleanup) { failure.addSuppressed(cleanup); }
                }
                if (failure instanceof Exception exception) throw exception;
                throw new CompletionException(failure);
            }));
        }).exceptionally(error -> {
            templateReady = false;
            templateError = rootMessage(error);
            PluginLogger.warning("城邦世界模板验证失败: " + templateError);
            return false;
        });
    }

    public CompletionStage<Void> provision(CityState cityState, CityWorldOperation operation) {
        if (!accepting) return CompletableFuture.failedFuture(new IllegalStateException("世界服务正在关闭"));
        if (!templateReady) return CompletableFuture.failedFuture(new IllegalStateException(
                "模板不可用: " + (templateError == null ? "尚未验证" : templateError)));
        WorldProvisionSpec spec = new WorldProvisionSpec(cityState.getUuid(), operation.id(),
                templateSnapshotName(), cityState.getWorldName(),
                MainSettings.getCityStateWorldTemplateRevision(), cityState.getWorldBorderSize());

        return onMain(() -> {
            operation.phase("COPYING");
            return backend.prepareCopy(spec);
        }).thenCompose(prepared -> runIo(() -> {
            prepared.copy();
            Path targetFolder = backend.worldFolder(spec.targetWorld());
            Files.deleteIfExists(targetFolder.resolve(TEMPLATE_MARKER_FILE));
            Files.deleteIfExists(targetFolder.resolve(BundledCityTemplateInstaller.MARKER));
            writeOwnershipMarker(spec, targetFolder);
            return null;
        })).thenCompose(ignored -> onMain(() -> {
            operation.phase("INITIALIZING");
            World world = backend.load(spec.targetWorld());
            backend.configureManagedWorld(spec.targetWorld(), world);
            world.getWorldBorder().setSize(spec.borderSize());
            world.setPVP(false);
            return world;
        })).thenCompose(world -> wishTreeVisualService == null
                ? CompletableFuture.completedFuture(null)
                : wishTreeVisualService.pasteInitial(cityState, world, operation)
        ).thenCompose(ignored -> onMain(() -> {
            World world = Bukkit.getWorld(spec.targetWorld());
            if (world == null) throw new IllegalStateException("粘贴后城邦世界意外卸载");
            operation.phase("PROTECTING");
            return world;
        })).thenCompose(world -> protection.apply(cityState, world, spec.borderSize())
                .thenApply(ignored -> world)
        ).thenCompose(world -> onMain(() -> {
            if (wishTreeVisualService != null) wishTreeVisualService.ensureInteraction(cityState, world);
            cityState.setSpawn(new Location(world, MainSettings.getWishTreeSpawnX(), MainSettings.getWishTreeSpawnY(),
                    MainSettings.getWishTreeSpawnZ(), MainSettings.getWishTreeSpawnYaw(), MainSettings.getWishTreeSpawnPitch()));
            backend.save(world);
            if (!backend.unload(spec.targetWorld(), true)) {
                throw new IllegalStateException("初始化后无法卸载城邦世界");
            }
            operation.phase("WORLD_READY");
            return null;
        }));
    }

    public CompletionStage<Void> provisionLegacy(CityState cityState) {
        if (cityState.getWorldState() == CityWorldState.READY) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> existing = legacyProvisions.get(cityState.getUuid());
        if (existing != null) return existing;
        if (cityState.getWorldState() != CityWorldState.UNASSIGNED) {
            return CompletableFuture.failedFuture(new IllegalStateException("城邦世界正在处理其他操作"));
        }
        UUID operationId = UUID.randomUUID();
        CityWorldOperation operation = operationStore.create(operationId, cityState.getUuid(), cityState.getWorldName(),
                WorldOperationKind.LEGACY_PROVISION);
        cityState.transitionWorld(CityLifecycleState.ACTIVE, CityWorldState.PROVISIONING, null);
        CompletableFuture<Void> result = new CompletableFuture<>();
        legacyProvisions.put(cityState.getUuid(), result);
        provision(cityState, operation).whenComplete((ignored, error) -> onMainNow(() -> {
            if (error == null) {
                cityState.transitionWorld(CityLifecycleState.ACTIVE, CityWorldState.READY, null);
                operation.complete();
                operationStore.remove(operation);
                result.complete(null);
            } else {
                Throwable root = unwrap(error);
                operation.error(root);
                cleanupProvisionedWorld(cityState, operation).whenComplete((cleaned, cleanupError) -> onMainNow(() -> {
                    if (cleanupError == null) {
                        cityState.transitionWorld(CityLifecycleState.ACTIVE, CityWorldState.UNASSIGNED,
                                "上次世界生成失败，请重试");
                        operation.complete();
                        operationStore.remove(operation);
                    } else {
                        Throwable cleanup = unwrap(cleanupError);
                        root.addSuppressed(cleanup);
                        operation.error(cleanup);
                        cityState.transitionWorld(CityLifecycleState.ERROR, CityWorldState.ERROR,
                                rootMessage(cleanup));
                    }
                    result.completeExceptionally(root);
                }));
            }
        }));
        result.whenComplete((ignored, error) -> onMainNow(() ->
                legacyProvisions.remove(cityState.getUuid(), result)));
        return result;
    }

    public CompletionStage<EnterResult> enter(Player player, CityState cityState) {
        if (!Bukkit.isPrimaryThread()) return onMain(() -> enter(player, cityState)).thenCompose(stage -> stage);
        if (!accepting) return CompletableFuture.completedFuture(EnterResult.failed("世界服务正在关闭"));
        if (!plugin.getCityStateLifecycleService().isReady()) {
            return CompletableFuture.completedFuture(EnterResult.failed("城邦世界系统正在恢复，请稍后重试"));
        }
        if (plugin.getCityStateLifecycleService().hasPending(cityState.getUuid())) {
            return CompletableFuture.completedFuture(EnterResult.failed("该城邦有待处理的世界操作，请联系管理员或稍后重试"));
        }
        if (!canAccess(player, cityState)) return CompletableFuture.completedFuture(EnterResult.failed("该城邦未开放参观"));
        if (plugin.getTeleportService().hasPending(player)) {
            return CompletableFuture.completedFuture(EnterResult.failed("已有传送正在处理中"));
        }
        if (cityState.getWorldState() != CityWorldState.READY && cityState.getWorldState() != CityWorldState.UNASSIGNED) {
            return CompletableFuture.completedFuture(EnterResult.failed("城邦世界当前不可用: " + cityState.getWorldState()));
        }
        CompletionStage<Void> preparation = cityState.getWorldState() == CityWorldState.UNASSIGNED
                ? provisionLegacy(cityState) : CompletableFuture.completedFuture(null);
        acquireLease(cityState.getWorldName());
        CompletableFuture<EnterResult> result = new CompletableFuture<>();
        preparation.thenCompose(ignored -> ensureLoaded(cityState)).whenComplete((world, loadError) -> onMainNow(() -> {
            if (loadError != null) {
                releaseLease(cityState.getWorldName());
                result.complete(EnterResult.failed(rootMessage(loadError)));
                return;
            }
            if (!player.isOnline() || !canAccess(player, cityState)) {
                releaseLease(cityState.getWorldName());
                result.complete(EnterResult.failed("进入资格已经失效"));
                return;
            }
            rememberReturnIfRequired(player);
            Location destination = cityState.hasSpawn() && cityState.getSpawn().getLocation() != null
                    ? cityState.getSpawn().getLocation() : world.getSpawnLocation();
            pendingEntries.put(player.getUniqueId(), cityState.getWorldName());
            boolean started = plugin.getTeleportService().begin(player, destination,
                    MainSettings.getCityStateSpawnTeleportWait(),
                    remaining -> showTeleportTitle(player, "count_down",
                            new PlaceholderContainer().add("count_down", remaining)),
                    () -> {
                        pendingEntries.remove(player.getUniqueId());
                        releaseLease(cityState.getWorldName());
                        showTeleportTitle(player, "teleported", new PlaceholderContainer());
                        result.complete(EnterResult.ok());
                    },
                    () -> {
                        pendingEntries.remove(player.getUniqueId());
                        releaseLease(cityState.getWorldName());
                        showTeleportTitle(player, "cancelled", new PlaceholderContainer());
                        result.complete(EnterResult.failed("传送已取消"));
                    },
                    failure -> {
                        pendingEntries.remove(player.getUniqueId());
                        releaseLease(cityState.getWorldName());
                        result.complete(EnterResult.failed(failure.getMessage()));
                    });
            if (!started && !result.isDone()) {
                pendingEntries.remove(player.getUniqueId());
                releaseLease(cityState.getWorldName());
                result.complete(EnterResult.failed("已有传送正在处理中"));
            }
        }));
        return result;
    }

    private void showTeleportTitle(Player player, String state, PlaceholderContainer placeholders) {
        String path = "CityStateMineGUI.city_state_spawn." + state;
        String title = plugin.getLangYaml().getString(path + ".title", "");
        String subtitle = plugin.getLangYaml().getString(path + ".subtitle", "");
        player.sendTitle(
                LegacyText.getColoredText(PlaceholderText.replacePlaceholders(title, placeholders)),
                LegacyText.getColoredText(PlaceholderText.replacePlaceholders(subtitle, placeholders)),
                0, 20, 10);
    }

    public CompletionStage<ExitResult> exit(Player player) {
        if (!Bukkit.isPrimaryThread()) return onMain(() -> exit(player)).thenCompose(stage -> stage);
        CityState current = findCityByWorld(player.getWorld().getName());
        if (current == null && !isRecoveryWorld(player.getWorld().getName())
                && !isTemplateWorld(player.getWorld().getName())) {
            return CompletableFuture.completedFuture(ExitResult.failed("你当前不在城邦世界中"));
        }
        CityStatePlayer data = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        Location destination = data.getWorldReturnLocation();
        if (destination == null || isManagedWorld(destination.getWorld().getName())) destination = fallbackLocation();
        if (destination == null) return CompletableFuture.completedFuture(ExitResult.failed("大厅世界不可用"));
        String previousWorld = player.getWorld().getName();
        CompletableFuture<ExitResult> result = new CompletableFuture<>();
        player.teleportAsync(destination).whenComplete((success, error) -> onMainNow(() -> {
            if (error != null || !Boolean.TRUE.equals(success)) {
                result.complete(ExitResult.failed(error == null ? "传送被服务器拒绝" : rootMessage(error)));
                return;
            }
            data.clearWorldReturnLocation();
            scheduleUnloadIfEmpty(previousWorld);
            result.complete(ExitResult.ok());
        }));
        return result;
    }

    public CompletableFuture<World> ensureLoaded(CityState cityState) {
        if (!Bukkit.isPrimaryThread()) return onMain(() -> ensureLoaded(cityState)).thenCompose(value -> value);
        if (!cityState.isWorldReady()) return CompletableFuture.failedFuture(new IllegalStateException("世界尚未准备完成"));
        World loaded = Bukkit.getWorld(cityState.getWorldName());
        if (loaded != null) {
            cancelUnload(cityState.getWorldName());
            try {
                loaded.setPVP(false);
                loaded.getWorldBorder().setSize(cityState.getWorldBorderSize());
                return synchronizeLoadedCity(cityState, loaded);
            } catch (Exception exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }
        CompletableFuture<World> existing = loads.get(cityState.getWorldName());
        if (existing != null) return existing;
        try {
            if (!backend.exists(cityState.getWorldName())) {
                throw new IllegalStateException("READY 城邦世界目录丢失，已拒绝生成空地图");
            }
            World world = backend.load(cityState.getWorldName());
            backend.configureManagedWorld(cityState.getWorldName(), world);
            world.setPVP(false);
            world.getWorldBorder().setSize(cityState.getWorldBorderSize());
            CompletableFuture<World> future = synchronizeLoadedCity(cityState, world);
            loads.put(cityState.getWorldName(), future);
            future.whenComplete((ignored, error) -> onMainNow(() ->
                    loads.remove(cityState.getWorldName(), future)));
            return future;
        } catch (Exception exception) {
            if (!backend.exists(cityState.getWorldName())) {
                cityState.transitionWorld(CityLifecycleState.ERROR, CityWorldState.ERROR, exception.getMessage());
            }
            return CompletableFuture.failedFuture(exception);
        }
    }

    private CompletableFuture<World> synchronizeLoadedCity(CityState cityState, World world) {
        CompletableFuture<World> result = new CompletableFuture<>();
        synchronizeProtectionNow(cityState).whenComplete((ignored, error) -> onMainNow(() -> {
            if (error != null) {
                Throwable failure = unwrap(error);
                result.completeExceptionally(failure);
                return;
            }
            try {
                if (Bukkit.getWorld(cityState.getWorldName()) != world) {
                    throw new IllegalStateException("区域成员同步期间城邦世界被卸载");
                }
                if (wishTreeVisualService != null) wishTreeVisualService.ensureInteraction(cityState, world);
                if (guardianVisualService != null) {
                    guardianVisualService.ensureVisual(cityState).exceptionally(visualError -> null);
                }
                result.complete(world);
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        }));
        return result;
    }

    public boolean canAccess(Player player, CityState cityState) {
        if (cityState == null || cityState.getLifecycleState() != CityLifecycleState.ACTIVE) return false;
        if (player.hasPermission("rookiecitystate.admin")) return true;
        if (isProtectionSyncPending(cityState.getUuid())) return false;
        return cityState.isMember(player.getUniqueId()) || cityState.getWorldVisibility() == WorldVisibility.PUBLIC;
    }

    public void rememberReturnIfRequired(Player player) {
        if (!isManagedWorld(player.getWorld().getName())) {
            plugin.getCityStatePlayerManager().getCityStatePlayer(player).rememberWorldReturn(player.getLocation());
        }
    }

    public void setVisibility(CityState cityState, WorldVisibility visibility) {
        cityState.setWorldVisibility(visibility);
        if (visibility == WorldVisibility.PRIVATE) {
            cancelPendingEntries(cityState.getWorldName(), player -> !cityState.isMember(player.getUniqueId())
                    && !player.hasPermission("rookiecitystate.admin"));
            World world = Bukkit.getWorld(cityState.getWorldName());
            if (world != null) {
                for (Player player : new ArrayList<>(world.getPlayers())) {
                    if (!canAccess(player, cityState)) exit(player);
                }
            }
        }
    }

    public void synchronizeProtection(CityState cityState) {
        if (cityState == null || !cityState.isWorldReady()) return;
        synchronizeProtectionNow(cityState).exceptionally(error -> null);
    }

    public void recoverProtectionSyncs() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::recoverProtectionSyncs);
            return;
        }
        List<ProtectionSyncOutbox.Entry> recovered = protectionSyncOutbox.loadAll();
        for (ProtectionSyncOutbox.Entry entry : recovered) {
            if (plugin.getCityStateManager().getCityState(entry.cityId()) == null) {
                PluginLogger.warning("成员同步 outbox 对应城邦当前未加载，已保留待管理员检查: " + entry.cityId());
            }
        }
        for (CityState cityState : plugin.getCityStateManager().getCityStates()) {
            if (cityState.isWorldReady()) synchronizeProtection(cityState);
        }
    }

    public boolean isProtectionSyncPending(UUID cityId) {
        return cityId != null && protectionSyncOutbox.has(cityId);
    }

    public boolean isProtectionSyncPending(String worldName) {
        CityState cityState = findCityByWorld(worldName);
        return cityState != null && isProtectionSyncPending(cityState.getUuid());
    }

    private CompletionStage<Void> synchronizeProtectionNow(CityState cityState) {
        if (!Bukkit.isPrimaryThread()) {
            return onMain(() -> synchronizeProtectionNow(cityState)).thenCompose(stage -> stage);
        }
        ProtectionSyncOutbox.Entry target = protectionSyncOutbox.stage(cityState);
        CompletableFuture<Void> existing = protectionSyncs.get(cityState.getUuid());
        if (existing != null) return existing;
        BukkitTask retry = protectionSyncRetries.remove(cityState.getUuid());
        if (retry != null) retry.cancel();
        CompletableFuture<Void> result = new CompletableFuture<>();
        protectionSyncs.put(cityState.getUuid(), result);
        CompletionStage<Void> synchronization;
        try { synchronization = protection.synchronizeMembers(cityState); }
        catch (Throwable error) { synchronization = CompletableFuture.failedFuture(error); }
        synchronization.whenComplete((ignored, error) -> onMainNow(() -> {
            protectionSyncs.remove(cityState.getUuid(), result);
            Throwable failure = error == null ? null : unwrap(error);
            if (failure == null && !protection.membersSynchronized(cityState)) {
                failure = new IllegalStateException("RookieRegions 回读结果与目标成员哈希不一致");
            }
            if (failure == null) {
                try {
                    boolean completed = protectionSyncOutbox.complete(target);
                    result.complete(null);
                    if (!completed) scheduleProtectionSyncRetry(cityState.getUuid(), 1L);
                } catch (RuntimeException completionError) {
                    ProtectionSyncOutbox.Entry failed = protectionSyncOutbox.failure(target, completionError,
                            System.currentTimeMillis() + 1000L);
                    scheduleProtectionSyncRetry(cityState.getUuid(), failed == null ? 1L : retryDelayTicks(failed.attempts()));
                    result.completeExceptionally(completionError);
                }
                return;
            }
            long retryTicks = retryDelayTicks(target.attempts() + 1);
            long nextRetryAt = System.currentTimeMillis() + retryTicks * 50L;
            ProtectionSyncOutbox.Entry failed = protectionSyncOutbox.failure(target, failure, nextRetryAt);
            if (failed != null && failed.hash().equals(target.hash())) {
                scheduleProtectionSyncRetry(cityState.getUuid(), retryTicks);
            } else {
                scheduleProtectionSyncRetry(cityState.getUuid(), 1L);
            }
            PluginLogger.warning("同步城邦区域成员失败 " + cityState.getName() + "，将在 "
                    + Math.max(1L, retryTicks / 20L) + " 秒后重试: " + rootMessage(failure));
            result.completeExceptionally(failure);
        }));
        return result;
    }

    private void scheduleProtectionSyncRetry(UUID cityId, long ticks) {
        if (!accepting || !plugin.isEnabled()) return;
        BukkitTask previous = protectionSyncRetries.remove(cityId);
        if (previous != null) previous.cancel();
        protectionSyncRetries.put(cityId, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            protectionSyncRetries.remove(cityId);
            CityState current = plugin.getCityStateManager().getCityState(cityId);
            if (current == null || !current.isWorldReady()) {
                scheduleProtectionSyncRetry(cityId, 6000L);
                return;
            }
            synchronizeProtectionNow(current).exceptionally(error -> null);
        }, Math.max(1L, ticks)));
    }

    private long retryDelayTicks(int attempts) {
        return switch (attempts) {
            case 0, 1 -> 20L;
            case 2 -> 100L;
            case 3 -> 600L;
            case 4 -> 1200L;
            case 5 -> 2400L;
            default -> 6000L;
        };
    }

    public void handleMembershipRemoved(CityState cityState, UUID playerId) {
        synchronizeProtection(cityState);
        cancelPendingEntries(cityState.getWorldName(), pending -> pending.getUniqueId().equals(playerId));
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.getWorld().getName().equals(cityState.getWorldName())) exit(player);
    }

    public @Nullable CityState findCityByWorld(String worldName) {
        if (worldName == null || !worldName.startsWith(WORLD_PREFIX)) return null;
        for (CityState cityState : plugin.getCityStateManager().getCityStates()) {
            if (worldName.equals(cityState.getWorldName())) return cityState;
        }
        return null;
    }

    public boolean isManagedWorld(String worldName) {
        return worldName != null && (worldName.startsWith(WORLD_PREFIX) || worldName.startsWith(RECOVERY_PREFIX)
                || worldName.startsWith(TEMPLATE_PREFIX) || worldName.equals(MainSettings.getCityStateWorldTemplate()));
    }

    public boolean isRecoveryWorld(String worldName) {
        return worldName != null && worldName.startsWith(RECOVERY_PREFIX);
    }

    public boolean isTemplateWorld(String worldName) {
        return worldName != null && (worldName.startsWith(TEMPLATE_PREFIX)
                || worldName.equals(MainSettings.getCityStateWorldTemplate()));
    }

    public Location getFallbackLocation() { return fallbackLocation(); }

    public CityWorldView getWorldView(CityState cityState) {
        return new CityWorldView(cityState.getUuid(), cityState.getWorldName(), cityState.getLifecycleState(),
                cityState.getWorldState(), cityState.getWorldVisibility(),
                Bukkit.getWorld(cityState.getWorldName()) != null, cityState.getWorldLastError());
    }

    public CompletionStage<Boolean> forceUnload(String worldName) {
        return onMain(() -> {
            if (leases.getOrDefault(worldName, 0) > 0 || loads.containsKey(worldName)) {
                throw new IllegalStateException("世界仍有传送或加载租约");
            }
            World world = Bukkit.getWorld(worldName);
            if (world == null) return true;
            if (!world.getPlayers().isEmpty()) throw new IllegalStateException("世界中仍有玩家");
            return backend.unload(worldName, true);
        });
    }

    public void onWorldEntered(String worldName) { cancelUnload(worldName); }
    public void onWorldLeft(String worldName) { scheduleUnloadIfEmpty(worldName); }

    public void acquireMaintenanceLease(String worldName) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("世界租约必须在主线程获取");
        acquireLease(worldName);
    }

    public void releaseMaintenanceLease(String worldName) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("世界租约必须在主线程释放");
        releaseLease(worldName);
    }

    public void reconcileLoadedWorlds() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::reconcileLoadedWorlds);
            return;
        }
        for (World world : new ArrayList<>(Bukkit.getWorlds())) {
            if (!isManagedWorld(world.getName()) || !world.getPlayers().isEmpty()) continue;
            try {
                CityState cityState = findCityByWorld(world.getName());
                backend.configureManagedWorld(world.getName(), world);
                CompletionStage<Void> synchronization = cityState != null && cityState.isWorldReady()
                        ? synchronizeProtectionNow(cityState)
                        : CompletableFuture.completedFuture(null);
                synchronization.whenComplete((ignored, error) -> onMainNow(() -> {
                    if (error != null) {
                        PluginLogger.warning("启动核对区域失败 " + world.getName() + ": " + rootMessage(error));
                        return;
                    }
                    try {
                        if (!backend.unload(world.getName(), true)) {
                            PluginLogger.warning("启动核对未能卸载空世界: " + world.getName());
                        } else if (cityState == null && !isTemplateWorld(world.getName())) {
                            PluginLogger.warning("已卸载未归属的 RookieCityState 世界，未自动删除: " + world.getName());
                        }
                    } catch (Exception exception) {
                        PluginLogger.warning("启动核对卸载世界失败 " + world.getName() + ": " + exception.getMessage());
                    }
                }));
            } catch (Exception exception) {
                PluginLogger.warning("启动核对世界失败 " + world.getName() + ": " + exception.getMessage());
            }
        }
    }

    public void evacuate(CityState cityState) {
        World world = Bukkit.getWorld(cityState.getWorldName());
        if (world == null) return;
        for (Player player : new ArrayList<>(world.getPlayers())) exit(player);
    }

    public CompletionStage<Void> evacuateAsync(CityState cityState) {
        World world = Bukkit.getWorld(cityState.getWorldName());
        if (world == null || world.getPlayers().isEmpty()) return CompletableFuture.completedFuture(null);
        CompletableFuture<?>[] exits = new ArrayList<>(world.getPlayers()).stream()
                .map(this::exit).map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(exits);
    }

    public CompletionStage<Path> archive(CityState cityState, CityWorldOperation operation) {
        return runIo(() -> {
            claimOwnershipOperation(cityState, operation);
            return null;
        }).thenCompose(ignored -> onMain(() -> {
            cancelPendingEntries(cityState.getWorldName(), player -> true);
            return evacuateAsync(cityState);
        })).thenCompose(stage -> stage)
        .thenCompose(ignored -> protection.removeWorld(cityState.getWorldName()))
        .thenCompose(ignored -> onMain(() -> {
            World loaded = Bukkit.getWorld(cityState.getWorldName());
            if (loaded != null && !loaded.getPlayers().isEmpty()) throw new IllegalStateException("世界中仍有玩家");
            if (!backend.unload(cityState.getWorldName(), true)) throw new IllegalStateException("无法卸载待归档世界");
            backend.forget(cityState.getWorldName());
            operation.phase("ARCHIVING");
            return backend.worldFolder(cityState.getWorldName());
        })).thenCompose(source -> runIo(() -> archiveOnDisk(cityState, operation, source)));
    }

    public CompletionStage<Void> cleanupProvisionedWorld(CityState cityState, CityWorldOperation operation) {
        return cleanupProvisionedWorld(cityState.getUuid(), cityState.getWorldName(), operation);
    }

    public CompletionStage<Void> cleanupProvisionedWorld(UUID cityId, String worldName,
                                                          CityWorldOperation operation) {
        return onMain(() -> {
            cancelPendingEntries(worldName, player -> true);
            World world = Bukkit.getWorld(worldName);
            if (world == null || world.getPlayers().isEmpty()) {
                return CompletableFuture.<Void>completedFuture(null);
            }
            CompletableFuture<?>[] exits = new ArrayList<>(world.getPlayers()).stream()
                    .map(this::exit).map(CompletionStage::toCompletableFuture)
                    .toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(exits);
        }).thenCompose(stage -> stage)
                .thenCompose(ignored -> protection.removeWorld(worldName))
                .thenCompose(ignored -> onMain(() -> {
            World world = Bukkit.getWorld(worldName);
            if (world != null && !world.getPlayers().isEmpty()) {
                throw new IllegalStateException("清理世界中仍有玩家");
            }
            if (world != null && !backend.unload(worldName, false)) {
                throw new IllegalStateException("无法卸载待清理世界");
            }
            if (Bukkit.getWorld(worldName) != null) {
                throw new IllegalStateException("待清理世界仍处于加载状态");
            }
            backend.forget(worldName);
            return backend.worldFolder(worldName);
        })).thenCompose(path -> runIo(() -> {
            if (!Files.exists(path)) return null;
            Path markerPath = path.resolve(MARKER_FILE);
            if (!Files.isRegularFile(markerPath)) {
                Path quarantine = path.resolveSibling(path.getFileName() + ".orphan-" + System.currentTimeMillis());
                Files.move(path, quarantine);
                PluginLogger.warning("未完整标记的世界已隔离而非删除: " + quarantine);
                return null;
            }
            YamlConfiguration marker = YamlFiles.load(markerPath.toFile());
            if (!cityId.toString().equals(marker.getString("city_uuid"))
                    || !operation.id().toString().equals(marker.getString("operation_id"))) {
                throw new IllegalStateException("拒绝清理归属不匹配的世界");
            }
            deleteTree(path);
            return null;
        }));
    }

    CompletionStage<Void> verifyProvisionedWorld(CityState cityState, CityWorldOperation operation) {
        return runIo(() -> {
            Path folder = backend.worldFolder(cityState.getWorldName());
            if (!Files.isDirectory(folder)) throw new IllegalStateException("已扣款操作的城邦世界目录丢失");
            verifyManagedSource(folder, cityState.getUuid(), operation.id());
            return null;
        });
    }

    public List<String> listArchives() {
        Path root = archiveRoot();
        if (!Files.isDirectory(root)) return List.of();
        try (var stream = Files.walk(root, 3)) {
            return stream.filter(path -> path.getFileName().toString().equals("archive.yml"))
                    .filter(path -> Files.isDirectory(path.getParent().resolve("world")))
                    .map(path -> root.relativize(path.getParent()).toString().replace('\\', '/')).sorted().toList();
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取归档列表", exception);
        }
    }

    public String findArchiveByOperation(UUID operationId) {
        for (String archiveId : listArchives()) {
            Path folder = resolveArchive(archiveId);
            if (!Files.isDirectory(folder.resolve("world"))) continue;
            YamlConfiguration manifest = YamlFiles.load(folder.resolve("archive.yml").toFile());
            if (operationId.toString().equals(manifest.getString("operation_id"))) {
                verifyArchiveOwnership(archiveId, folder, manifest);
                return archiveId;
            }
        }
        return null;
    }

    public CompletionStage<String> restoreArchive(String archiveId) {
        Path archive = resolveArchive(archiveId);
        Path manifest = archive.resolve("archive.yml");
        if (!Files.isRegularFile(manifest)) return CompletableFuture.failedFuture(new IllegalArgumentException("归档不存在"));
        verifyArchiveOwnership(archiveId, archive, YamlFiles.load(manifest.toFile()));
        String recoveryName = RECOVERY_PREFIX + UUID.randomUUID().toString().replace("-", "");
        Path target = backend.worldFolder(recoveryName);
        return runIo(() -> {
            copyTree(archive.resolve("world"), target, true);
            YamlConfiguration marker = new YamlConfiguration();
            marker.set("recovery_of", archiveId);
            marker.set("created_at", System.currentTimeMillis());
            YamlFiles.save(marker, target.resolve(MARKER_FILE).toFile());
            return recoveryName;
        }).thenCompose(name -> onMain(() -> {
            World world = backend.load(name);
            backend.configureManagedWorld(name, world);
            world.setPVP(false);
            return name;
        }));
    }

    public CompletionStage<Boolean> deleteRecovery(String worldName) {
        if (!worldName.startsWith(RECOVERY_PREFIX)) return CompletableFuture.failedFuture(new IllegalArgumentException("不是恢复世界"));
        return protection.removeWorld(worldName).thenCompose(ignored -> onMain(() -> {
            World world = Bukkit.getWorld(worldName);
            if (world != null && !world.getPlayers().isEmpty()) throw new IllegalStateException("恢复世界中仍有玩家");
            if (world != null && !backend.unload(worldName, true)) {
                throw new IllegalStateException("无法卸载恢复世界");
            }
            if (Bukkit.getWorld(worldName) != null) {
                throw new IllegalStateException("恢复世界仍处于加载状态");
            }
            backend.forget(worldName);
            return backend.worldFolder(worldName);
        })).thenCompose(path -> runIo(() -> {
            Path marker = path.resolve(MARKER_FILE);
            if (!Files.isRegularFile(marker) || !YamlFiles.load(marker.toFile()).contains("recovery_of")) {
                throw new IllegalStateException("恢复世界归属标记无效");
            }
            deleteTree(path);
            return true;
        }));
    }

    public void purgeExpiredArchives() {
        long retentionMillis = Duration.ofDays(MainSettings.getCityStateWorldArchiveRetentionDays()).toMillis();
        runIo(() -> {
            Path root = archiveRoot();
            if (!Files.isDirectory(root)) return null;
            for (String archiveId : listArchives()) {
                try {
                    Path folder = resolveArchive(archiveId);
                    YamlConfiguration manifest = YamlFiles.load(folder.resolve("archive.yml").toFile());
                    long archivedAt = manifest.getLong("archived_at");
                    long purgeAfter = manifest.getLong("purge_after",
                            archivedAt > 0 ? archivedAt + retentionMillis : Long.MAX_VALUE);
                    if (System.currentTimeMillis() >= purgeAfter) {
                        verifyArchiveOwnership(archiveId, folder, manifest);
                        deleteTree(folder);
                    }
                } catch (RuntimeException | IOException exception) {
                    PluginLogger.warning("清理城邦归档失败 " + archiveId + ": " + exception.getMessage());
                }
            }
            return null;
        }).exceptionally(error -> {
            PluginLogger.warning("清理过期城邦归档失败: " + rootMessage(error));
            return null;
        });
    }

    public void shutdown() {
        accepting = false;
        unloadTasks.values().forEach(BukkitTask::cancel);
        unloadTasks.clear();
        protectionSyncRetries.values().forEach(BukkitTask::cancel);
        protectionSyncRetries.clear();
        pendingEntries.clear();
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) ioExecutor.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            ioExecutor.shutdownNow();
        }
    }

    private void acquireLease(String worldName) {
        cancelUnload(worldName);
        leases.merge(worldName, 1, Integer::sum);
    }

    private void releaseLease(String worldName) {
        int next = leases.getOrDefault(worldName, 1) - 1;
        if (next <= 0) leases.remove(worldName); else leases.put(worldName, next);
        scheduleUnloadIfEmpty(worldName);
    }

    private void cancelUnload(String worldName) {
        BukkitTask task = unloadTasks.remove(worldName);
        if (task != null) task.cancel();
    }

    private void cancelPendingEntries(String worldName, java.util.function.Predicate<Player> selector) {
        for (Map.Entry<UUID, String> entry : new ArrayList<>(pendingEntries.entrySet())) {
            if (!worldName.equals(entry.getValue())) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && selector.test(player)) {
                plugin.getTeleportService().cancel(player, true);
            } else if (player == null) {
                pendingEntries.remove(entry.getKey());
                releaseLease(worldName);
            }
        }
    }

    private void scheduleUnloadIfEmpty(String worldName) {
        CityState cityState = findCityByWorld(worldName);
        boolean templateWorld = isTemplateWorld(worldName);
        if ((cityState == null && !templateWorld) || Bukkit.getWorld(worldName) == null) return;
        cancelUnload(worldName);
        long ticks = Math.max(1L, MainSettings.getCityStateWorldUnloadDelaySeconds() * 20L);
        unloadTasks.put(worldName, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            unloadTasks.remove(worldName);
            World world = Bukkit.getWorld(worldName);
            if (world == null || !world.getPlayers().isEmpty() || leases.getOrDefault(worldName, 0) > 0
                    || (cityState != null && cityState.getLifecycleState() != CityLifecycleState.ACTIVE)) return;
            try {
                if (!backend.unload(worldName, true)) PluginLogger.warning("空闲城邦世界卸载失败: " + worldName);
            } catch (Exception exception) {
                PluginLogger.warning("空闲城邦世界卸载异常 " + worldName + ": " + exception.getMessage());
            }
        }, ticks));
    }

    private Location fallbackLocation() {
        World fallback = Bukkit.getWorld(MainSettings.getCityStateWorldFallbackWorld());
        if (fallback == null && !Bukkit.getWorlds().isEmpty()) fallback = Bukkit.getWorlds().getFirst();
        return fallback == null ? null : fallback.getSpawnLocation();
    }

    private void validateTemplateWorld(World template) {
        double configured = MainSettings.getCityStateWorldBorderSize();
        if (Math.abs(template.getWorldBorder().getSize() - configured) > 0.01D) {
            throw new IllegalStateException("模板边界必须为 " + MainSettings.getCityStateWorldBorderSize());
        }
        if (!template.getWorldBorder().isInside(template.getSpawnLocation())) {
            throw new IllegalStateException("模板出生点位于世界边界外");
        }
    }

    private String templateSnapshotName() {
        return TEMPLATE_PREFIX + MainSettings.getCityStateWorldTemplateRevision();
    }

    private void verifyTemplateSnapshot(Path folder) {
        Path markerPath = folder.resolve(TEMPLATE_MARKER_FILE);
        if (!Files.isRegularFile(markerPath)) {
            throw new IllegalStateException("模板快照缺少归属标记，拒绝覆盖: " + folder.getFileName());
        }
        YamlConfiguration marker = YamlFiles.load(markerPath.toFile());
        if (!MainSettings.getCityStateWorldTemplate().equals(marker.getString("template"))
                || MainSettings.getCityStateWorldTemplateRevision() != marker.getInt("template_revision")) {
            throw new IllegalStateException("模板快照与当前模板/修订不匹配，请提升 template_revision");
        }
    }

    private Path archiveOnDisk(CityState cityState, CityWorldOperation operation, Path source) throws Exception {
        verifyManagedSource(source, cityState.getUuid(), operation.id());
        long timestamp = System.currentTimeMillis();
        Path destination = archiveRoot().resolve(cityState.getUuid().toString()).resolve(Long.toString(timestamp)).normalize();
        if (!destination.startsWith(archiveRoot())) throw new IllegalStateException("归档路径越界");
        Files.createDirectories(destination);
        Path archivedWorld = destination.resolve("world");
        YamlConfiguration manifest = new YamlConfiguration();
        manifest.set("city_uuid", cityState.getUuid().toString());
        manifest.set("operation_id", operation.id().toString());
        manifest.set("original_world", cityState.getWorldName());
        manifest.set("archived_at", timestamp);
        manifest.set("purge_after", timestamp + Duration.ofDays(MainSettings.getCityStateWorldArchiveRetentionDays()).toMillis());
        YamlFiles.save(manifest, destination.resolve("archive.yml").toFile());
        try {
            Files.move(source, archivedWorld, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unsupported) {
            Path partial = destination.resolve("world.partial");
            if (Files.exists(partial)) throw new IllegalStateException("归档暂存目录已存在: " + partial);
            copyTree(source, partial, false);
            Files.move(partial, archivedWorld);
            deleteTree(source);
        }
        operation.set("archive.id", cityState.getUuid() + "/" + timestamp);
        return destination;
    }

    private void verifyManagedSource(Path source, UUID cityId, UUID operationId) {
        Path normalized = source.toAbsolutePath().normalize();
        Path root = backend.worldRoot().toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || !normalized.getFileName().toString().startsWith(WORLD_PREFIX)) {
            throw new IllegalStateException("拒绝归档非受管世界路径");
        }
        Path markerFile = normalized.resolve(MARKER_FILE);
        if (!Files.isRegularFile(markerFile)) throw new IllegalStateException("世界缺少归属标记");
        YamlConfiguration marker = YamlFiles.load(markerFile.toFile());
        if (!cityId.toString().equals(marker.getString("city_uuid"))
                || !operationId.toString().equals(marker.getString("operation_id"))) {
            throw new IllegalStateException("世界归属标记与删除操作不匹配");
        }
    }

    private void verifyArchiveOwnership(String archiveId, Path folder, YamlConfiguration manifest) {
        String expectedCity = archiveId.substring(0, archiveId.indexOf('/'));
        String operationId = manifest.getString("operation_id");
        if (!expectedCity.equals(manifest.getString("city_uuid")) || operationId == null) {
            throw new IllegalStateException("归档清单归属不匹配，拒绝清理: " + archiveId);
        }
        Path markerPath = folder.resolve("world").resolve(MARKER_FILE);
        if (!Files.isRegularFile(markerPath)) throw new IllegalStateException("归档缺少世界归属标记: " + archiveId);
        YamlConfiguration marker = YamlFiles.load(markerPath.toFile());
        if (!expectedCity.equals(marker.getString("city_uuid"))
                || !operationId.equals(marker.getString("operation_id"))) {
            throw new IllegalStateException("归档世界归属不匹配，拒绝清理: " + archiveId);
        }
    }

    private void writeOwnershipMarker(WorldProvisionSpec spec, Path folder) {
        YamlConfiguration marker = new YamlConfiguration();
        marker.set("city_uuid", spec.cityStateId().toString());
        marker.set("operation_id", spec.operationId().toString());
        marker.set("backend", backend.name());
        marker.set("template", MainSettings.getCityStateWorldTemplate());
        marker.set("template_snapshot", spec.templateWorld());
        marker.set("template_revision", spec.templateRevision());
        marker.set("created_at", System.currentTimeMillis());
        YamlFiles.save(marker, folder.resolve(MARKER_FILE).toFile());
    }

    private void claimOwnershipOperation(CityState cityState, CityWorldOperation operation) {
        Path markerFile = backend.worldFolder(cityState.getWorldName()).resolve(MARKER_FILE);
        if (!Files.isRegularFile(markerFile)) throw new IllegalStateException("世界缺少归属标记");
        YamlConfiguration marker = YamlFiles.load(markerFile.toFile());
        if (!cityState.getUuid().toString().equals(marker.getString("city_uuid"))) {
            throw new IllegalStateException("世界归属标记中的城邦 UUID 不匹配");
        }
        marker.set("operation_id", operation.id().toString());
        marker.set("claimed_at", System.currentTimeMillis());
        YamlFiles.save(marker, markerFile.toFile());
    }

    private Path archiveRoot() {
        return plugin.getDataFolder().toPath().resolve("data").resolve("archives").toAbsolutePath().normalize();
    }

    private Path resolveArchive(String archiveId) {
        String safe = archiveId == null ? "" : archiveId.replace('\\', '/');
        if (!safe.matches("[0-9a-fA-F-]{36}/[0-9]{1,20}")) throw new IllegalArgumentException("归档 ID 无效");
        Path path = archiveRoot().resolve(safe).normalize();
        if (!path.startsWith(archiveRoot())) throw new IllegalArgumentException("归档路径越界");
        return path;
    }

    private static void copyTree(Path source, Path target, boolean sanitizeWorldIdentity) throws IOException {
        if (!Files.isDirectory(source)) throw new IOException("Source directory does not exist: " + source);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                if (sanitizeWorldIdentity && (name.equals("uid.dat") || name.equals("session.lock")
                        || name.equals(".paper-remapped"))) return FileVisitResult.CONTINUE;
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean isManagedWorldName(String name) {
        return name != null && (name.startsWith(WORLD_PREFIX) || name.startsWith(RECOVERY_PREFIX));
    }

    private record TemplatePreparation(String snapshotName, CityWorldBackend.PreparedCopy copy) { }
    private record LoadedTemplateSnapshot(TemplatePreparation preparation, World world) { }

    private <T> CompletableFuture<T> onMain(Callable<T> callable) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (Bukkit.isPrimaryThread()) {
            try { result.complete(callable.call()); } catch (Throwable error) { result.completeExceptionally(error); }
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try { result.complete(callable.call()); } catch (Throwable error) { result.completeExceptionally(error); }
            });
        }
        return result;
    }

    private void onMainNow(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) runnable.run(); else Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private <T> CompletableFuture<T> runIo(Callable<T> callable) {
        return CompletableFuture.supplyAsync(() -> {
            try { return callable.call(); }
            catch (Exception exception) { throw new java.util.concurrent.CompletionException(exception); }
        }, ioExecutor);
    }

    public <T> CompletionStage<T> runIoOperation(Callable<T> callable) {
        return runIo(callable);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String rootMessage(Throwable error) {
        Throwable root = unwrap(error);
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
