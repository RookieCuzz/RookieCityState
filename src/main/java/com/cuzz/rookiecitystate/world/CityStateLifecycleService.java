package com.cuzz.rookiecitystate.world;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.api.event.CityStateCreatedEvent;
import com.cuzz.rookiecitystate.api.event.CityStateDeletedEvent;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.CityStateManager;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.request.Request;
import com.cuzz.rookiecitystate.transaction.TransactionService;
import com.cuzz.rookiecitystate.world.operation.CityWorldOperation;
import com.cuzz.rookiecitystate.world.operation.PaymentState;
import com.cuzz.rookiecitystate.world.operation.WorldOperationKind;
import com.cuzz.rookiecitystate.world.operation.WorldOperationStore;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CityStateLifecycleService {
    private final RookieCityState plugin;
    private final CityWorldService worlds;
    private final WorldOperationStore operations;
    private final Set<UUID> reservedOwners = new HashSet<>();
    private final Set<String> reservedNames = new HashSet<>();
    private final Set<UUID> activeCities = new HashSet<>();
    private final AtomicBoolean recovering = new AtomicBoolean();
    private volatile boolean ready = true;

    public CityStateLifecycleService(RookieCityState plugin, CityWorldService worlds, WorldOperationStore operations) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.operations = operations;
    }

    public CompletionStage<CreationResult> create(CityStatePlayer owner, String name,
                                                   TransactionService.Payment payment,
                                                   String paymentType, double amount) {
        if (!Bukkit.isPrimaryThread()) {
            return CompletableFuture.failedFuture(new IllegalStateException("创建流程必须从服务器主线程发起"));
        }
        if (!ready) return CompletableFuture.completedFuture(CreationResult.failed("城邦世界系统正在恢复，请稍后重试"));
        try {
            validateAndReserve(owner, name, payment);
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(CreationResult.failed(message(error)));
        }

        UUID cityId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        CityWorldOperation operation = operations.create(operationId, cityId, CityState.managedWorldName(cityId),
                WorldOperationKind.CREATE);
        operation.payment(PaymentState.NOT_CHARGED, owner.getUuid(), paymentType, amount);
        CityState draft;
        try {
            draft = plugin.getCityStateManager().createProvisioningDraft(owner, name, cityId, operationId);
        } catch (RuntimeException error) {
            release(owner, name);
            operation.error(error);
            operation.complete();
            operations.remove(operation);
            return CompletableFuture.completedFuture(CreationResult.failed(message(error)));
        }

        CompletableFuture<CreationResult> result = new CompletableFuture<>();
        worlds.provision(draft, operation).whenComplete((ignored, provisionError) -> runMain(() -> {
            if (provisionError != null) {
                failBeforeCharge(draft, operation, owner, name, provisionError, result);
                return;
            }
            try {
                operation.phase("AWAITING_PAYMENT");
                if (!owner.isOnline()) throw new IllegalStateException("创建者已离线，创建已取消");
                validateReservation(owner, name);
                payment.validate();
                operation.payment(PaymentState.CHARGE_INTENT, owner.getUuid(), paymentType, amount);
                if (!payment.charge()) {
                    operation.payment(PaymentState.NOT_CHARGED, owner.getUuid(), paymentType, amount);
                    throw new IllegalStateException("扣款服务返回失败");
                }
                operation.payment(PaymentState.CHARGED, owner.getUuid(), paymentType, amount);
                operation.phase("COMMITTING");
                draft.transitionWorld(CityLifecycleState.ACTIVE, CityWorldState.READY, null);
                plugin.getCityStateManager().registerProvisionedCityState(draft);
                operation.complete();
                operations.remove(operation);
                release(owner, name);
                Bukkit.getPluginManager().callEvent(new CityStateCreatedEvent(draft, owner));
                result.complete(CreationResult.ok(draft));
            } catch (Throwable commitError) {
                handleCommitFailure(draft, operation, owner, name, payment, amount, paymentType, commitError, result);
            }
        }));
        return result;
    }

    public CompletionStage<DeletionResult> delete(CityState cityState) {
        if (!Bukkit.isPrimaryThread()) return CompletableFuture.failedFuture(
                new IllegalStateException("解散流程必须从服务器主线程发起"));
        if (!ready) return CompletableFuture.completedFuture(DeletionResult.failed("城邦世界系统正在恢复，请稍后重试"));
        if (!cityState.isValid()) return CompletableFuture.completedFuture(DeletionResult.failed("城邦已失效"));
        CityWorldState worldState = cityState.getWorldState();
        if (worldState == CityWorldState.PROVISIONING || worldState == CityWorldState.ARCHIVING) {
            return CompletableFuture.completedFuture(DeletionResult.failed("城邦世界正在处理其他操作，请稍后重试"));
        }
        if (worldState == CityWorldState.ERROR) {
            return CompletableFuture.completedFuture(DeletionResult.failed("城邦世界处于错误状态，请先执行恢复或联系管理员"));
        }
        if (worldState != CityWorldState.READY && worldState != CityWorldState.UNASSIGNED) {
            return CompletableFuture.completedFuture(DeletionResult.failed("当前世界状态不允许解散: " + worldState));
        }
        synchronized (activeCities) {
            if (!activeCities.add(cityState.getUuid())) {
                return CompletableFuture.completedFuture(DeletionResult.failed("该城邦已有生命周期操作正在进行"));
            }
        }
        UUID operationId = UUID.randomUUID();
        CityWorldOperation operation = operations.create(operationId, cityState.getUuid(), cityState.getWorldName(),
                WorldOperationKind.DELETE);
        cityState.transitionWorld(CityLifecycleState.DELETING, CityWorldState.ARCHIVING, null);
        CompletionStage<DeletionResult> deletion;
        if (worldState == CityWorldState.UNASSIGNED) {
            operation.phase("NO_WORLD");
            deletion = cleanAndFinalizeDeletion(cityState, operation);
        } else {
            operation.phase("EVACUATING");
            deletion = continueDelete(cityState, operation);
        }
        return deletion.whenComplete((ignored, error) -> {
            synchronized (activeCities) { activeCities.remove(cityState.getUuid()); }
        });
    }

    public boolean isReady() { return ready && !recovering.get(); }

    public boolean hasPending(UUID cityId) {
        synchronized (activeCities) {
            if (activeCities.contains(cityId)) return true;
        }
        return operations.loadAll().stream().anyMatch(operation -> operation.cityStateId().equals(cityId)
                && !operation.phase().equals("COMPLETE"));
    }

    public CompletionStage<Integer> recover() {
        if (!recovering.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("世界操作恢复已经在进行中"));
        }
        ready = false;
        try {
            List<CityWorldOperation> loaded = operations.loadAll();
            loaded.stream()
                    .filter(operation -> operation.phase().equals("COMPLETE"))
                    .forEach(operations::remove);
            List<CityWorldOperation> pending = loaded.stream()
                    .filter(operation -> !operation.phase().equals("COMPLETE")).toList();
            if (pending.isEmpty()) {
                recovering.set(false);
                ready = true;
                return CompletableFuture.completedFuture(0);
            }
            CompletionStage<Void> recovery = CompletableFuture.completedFuture(null);
            for (CityWorldOperation operation : pending) {
                recovery = recovery.thenCompose(ignored -> recoverSafely(operation).thenApply(done -> null));
            }
            return recovery.handle((ignored, error) -> {
                recovering.set(false);
                ready = true;
                return pending.size();
            });
        } catch (Throwable error) {
            recovering.set(false);
            ready = false;
            return CompletableFuture.failedFuture(error);
        }
    }

    public List<CityWorldOperation> getOperations() { return operations.loadAll(); }

    public CompletionStage<String> resolvePayment(UUID operationId, boolean charged) {
        CityWorldOperation operation = operations.get(operationId);
        if (operation == null || operation.paymentState() != PaymentState.PAYMENT_RECONCILIATION_REQUIRED) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("操作不存在或无需支付对账"));
        }
        CityState draft = new CityState(plugin.getCityStateManager().cityStateFile(operation.cityStateId()));
        if (charged) {
            return worlds.verifyProvisionedWorld(draft, operation).thenCompose(ignored -> runMainFuture(() -> {
                CityState existing = plugin.getCityStateManager().getCityState(operation.cityStateId());
                CityState committed = existing == null ? draft : existing;
                if (existing == null) {
                    draft.transitionWorld(CityLifecycleState.ACTIVE, CityWorldState.READY, null);
                    plugin.getCityStateManager().registerProvisionedCityState(draft);
                }
                operation.payment(PaymentState.CHARGED, UUID.fromString(operation.payer()),
                        operation.paymentType(), operation.paymentAmount());
                operation.complete();
                operations.remove(operation);
                CityStatePlayer owner = plugin.getCityStatePlayerManager().getCityStatePlayer(UUID.fromString(operation.payer()));
                Bukkit.getPluginManager().callEvent(new CityStateCreatedEvent(committed, owner));
                return "已按扣款成功完成城邦创建";
            }));
        }
        return worlds.cleanupProvisionedWorld(draft, operation).thenApply(ignored -> {
            deleteDraft(draft);
            operation.payment(PaymentState.NOT_CHARGED, UUID.fromString(operation.payer()),
                    operation.paymentType(), operation.paymentAmount());
            operation.complete();
            operations.remove(operation);
            return "已按未扣款清理创建草稿";
        });
    }

    private CompletionStage<DeletionResult> continueDelete(CityState cityState, CityWorldOperation operation) {
        return worlds.archive(cityState, operation).thenCompose(path -> cleanAndFinalizeDeletion(cityState, operation))
                .exceptionally(error -> {
            Throwable root = unwrap(error);
            operation.error(root);
            runMain(() -> cityState.transitionWorld(CityLifecycleState.ERROR, CityWorldState.ERROR, message(root)));
            return DeletionResult.failed(message(root));
        });
    }

    private CompletionStage<?> recoverOperation(CityWorldOperation operation) {
        try {
            if (operation.paymentState() == PaymentState.CHARGE_INTENT) {
                operation.payment(PaymentState.PAYMENT_RECONCILIATION_REQUIRED,
                        operation.payer() == null ? null : UUID.fromString(operation.payer()),
                        operation.paymentType(), operation.paymentAmount());
                PluginLogger.warning("支付状态需要人工核对，操作 ID=" + operation.id() + " 玩家="
                        + operation.payer() + " 金额=" + operation.paymentAmount());
                return CompletableFuture.completedFuture(null);
            }
            if (operation.paymentState() == PaymentState.REFUND_PENDING) {
                PluginLogger.warning("退款仍需人工处理，操作 ID=" + operation.id() + " 玩家="
                        + operation.payer() + " 金额=" + operation.paymentAmount());
                return CompletableFuture.completedFuture(null);
            }
            java.io.File cityFile = plugin.getCityStateManager().cityStateFile(operation.cityStateId());
            if (!cityFile.isFile()) {
                if (operation.kind() == WorldOperationKind.CREATE
                        && operation.paymentState() == PaymentState.NOT_CHARGED
                        && operation.phase().equals("PREPARED")) {
                    return worlds.cleanupProvisionedWorld(operation.cityStateId(), operation.worldName(), operation)
                            .thenApply(ignored -> {
                                operation.complete();
                                operations.remove(operation);
                                return null;
                            });
                }
                throw new IllegalStateException("操作对应的城邦数据不存在，已保留操作等待管理员对账");
            }
            CityState cityState = new CityState(cityFile);
            if (operation.paymentState() == PaymentState.CHARGED && operation.kind() == WorldOperationKind.CREATE) {
                return worlds.verifyProvisionedWorld(cityState, operation).thenCompose(ignored -> runMainFuture(() -> {
                    CityState existing = plugin.getCityStateManager().getCityState(operation.cityStateId());
                    CityState committed = existing == null ? cityState : existing;
                    if (existing == null) {
                        if (cityState.getLifecycleState() != CityLifecycleState.ACTIVE || !cityState.isWorldReady()) {
                            cityState.transitionWorld(CityLifecycleState.ACTIVE, CityWorldState.READY, null);
                        }
                        plugin.getCityStateManager().registerProvisionedCityState(cityState);
                    }
                    operation.complete();
                    operations.remove(operation);
                    if (committed.getOwner() == null) throw new IllegalStateException("城邦缺少会长，无法完成恢复");
                    CityStatePlayer owner = plugin.getCityStatePlayerManager()
                            .getCityStatePlayer(committed.getOwner().getUuid());
                    Bukkit.getPluginManager().callEvent(new CityStateCreatedEvent(committed, owner));
                    return null;
                }));
            }
            if (operation.kind() == WorldOperationKind.DELETE) {
                if (operation.phase().equals("NO_WORLD")) {
                    return cleanAndFinalizeDeletion(cityState, operation);
                }
                String archiveId = operation.getString("archive.id");
                if (archiveId == null) archiveId = worlds.findArchiveByOperation(operation.id());
                if (archiveId != null) {
                    operation.set("archive.id", archiveId);
                    return cleanAndFinalizeDeletion(cityState, operation);
                }
                return continueDelete(cityState, operation);
            }
            if (operation.kind() == WorldOperationKind.LEGACY_PROVISION) {
                CityState loaded = plugin.getCityStateManager().getCityState(operation.cityStateId());
                CityState target = loaded == null ? cityState : loaded;
                if (target.getWorldState() == CityWorldState.READY || operation.phase().equals("WORLD_READY")) {
                    return worlds.verifyProvisionedWorld(target, operation).thenCompose(ignored -> runMainFuture(() -> {
                        if (target.getWorldState() != CityWorldState.READY) {
                            target.transitionWorld(CityLifecycleState.ACTIVE, CityWorldState.READY, null);
                        }
                        operation.complete();
                        operations.remove(operation);
                        return null;
                    }));
                }
                return worlds.cleanupProvisionedWorld(cityState, operation).thenCompose(ignored -> runMainFuture(() -> {
                    cityState.transitionWorld(CityLifecycleState.ACTIVE, CityWorldState.UNASSIGNED,
                            "上次世界生成未完成，请重试");
                    operation.complete();
                    operations.remove(operation);
                    return null;
                }));
            }
            return worlds.cleanupProvisionedWorld(cityState, operation).handle((ignored, error) -> {
                deleteDraft(cityState);
                operation.complete();
                return null;
            });
        } catch (Throwable error) {
            operation.error(error);
            PluginLogger.warning("恢复世界操作失败 " + operation.id() + ": " + message(error));
            return CompletableFuture.failedFuture(error);
        }
    }

    private CompletionStage<Void> recoverSafely(CityWorldOperation operation) {
        return recoverOperation(operation).handle((ignored, error) -> {
            if (error != null) {
                Throwable root = unwrap(error);
                operation.error(root);
                PluginLogger.warning("恢复世界操作失败 " + operation.id() + ": " + message(root));
            }
            return null;
        });
    }

    private CompletionStage<DeletionResult> finalizeDeletion(CityState cityState, CityWorldOperation operation) {
        return runMainFuture(() -> {
            operation.phase("FINALIZING");
            plugin.getWishTreeService().archive(cityState);
            plugin.getGuardianBeastService().archive(cityState);
            plugin.getCitySocialService().archive(cityState);
            cityState.transitionWorld(CityLifecycleState.DELETED, CityWorldState.ARCHIVED, null);
            CityState loaded = plugin.getCityStateManager().getCityState(cityState.getUuid());
            if (loaded != null) plugin.getCityStateManager().unloadCityState(loaded);
            operation.complete();
            operations.remove(operation);
            String archiveId = operation.getString("archive.id");
            Bukkit.getPluginManager().callEvent(new CityStateDeletedEvent(cityState));
            return DeletionResult.ok(archiveId);
        });
    }

    private CompletionStage<DeletionResult> cleanAndFinalizeDeletion(CityState cityState,
                                                                      CityWorldOperation operation) {
        return runMainFuture(() -> {
            cleanRequests(cityState);
            return null;
        }).thenCompose(ignored -> worlds.runIoOperation(() ->
                plugin.getRequestManager().deletePersistedForCity(cityState.getUuid())))
                .thenCompose(ignored -> finalizeDeletion(cityState, operation));
    }

    private synchronized void validateAndReserve(CityStatePlayer owner, String name,
                                                 TransactionService.Payment payment) {
        if (!worlds.isTemplateReady()) throw new IllegalStateException("城邦世界模板不可用: " + worlds.getTemplateError());
        if (owner == null || owner.isInCityState()) throw new IllegalArgumentException("你已经加入其他城邦");
        String normalized = CityStateManager.normalizeName(name);
        if (normalized.isEmpty() || plugin.getCityStateManager().getCityStateByName(name) != null
                || reservedNames.contains(normalized)) throw new IllegalArgumentException("城邦名已经存在");
        for (CityWorldOperation pending : operations.loadAll()) {
            if (pending.kind() != WorldOperationKind.CREATE || pending.phase().equals("COMPLETE")) continue;
            if (owner.getUuid().toString().equals(pending.payer())) {
                throw new IllegalStateException("你有一个尚未处理完的创建或支付操作: " + pending.id());
            }
            java.io.File draftFile = plugin.getCityStateManager().cityStateFile(pending.cityStateId());
            if (draftFile.isFile() && normalized.equals(CityStateManager.normalizeName(
                    YamlFiles.load(draftFile).getString("name")))) {
                throw new IllegalStateException("该城邦名仍被未完成操作预留: " + pending.id());
            }
        }
        if (!reservedOwners.add(owner.getUuid())) throw new IllegalStateException("你已经有创建任务正在进行");
        reservedNames.add(normalized);
        try { payment.validate(); }
        catch (RuntimeException error) {
            reservedOwners.remove(owner.getUuid());
            reservedNames.remove(normalized);
            throw error;
        }
    }

    private synchronized void validateReservation(CityStatePlayer owner, String name) {
        if (!reservedOwners.contains(owner.getUuid()) || !reservedNames.contains(CityStateManager.normalizeName(name))) {
            throw new IllegalStateException("创建预留已经失效");
        }
        if (owner.isInCityState() || plugin.getCityStateManager().getCityStateByName(name) != null) {
            throw new IllegalStateException("创建条件已经发生变化");
        }
    }

    private synchronized void release(CityStatePlayer owner, String name) {
        reservedOwners.remove(owner.getUuid());
        reservedNames.remove(CityStateManager.normalizeName(name));
    }

    private void failBeforeCharge(CityState draft, CityWorldOperation operation, CityStatePlayer owner,
                                  String name, Throwable error, CompletableFuture<CreationResult> result) {
        Throwable root = unwrap(error);
        operation.error(root);
        worlds.cleanupProvisionedWorld(draft, operation).whenComplete((ignored, cleanupError) -> {
            if (cleanupError == null) {
                deleteDraft(draft);
                operation.complete();
                operations.remove(operation);
            } else {
                operation.error(unwrap(cleanupError));
            }
            release(owner, name);
            result.complete(CreationResult.failed(message(root)));
        });
    }

    private void handleCommitFailure(CityState draft, CityWorldOperation operation, CityStatePlayer owner,
                                     String name, TransactionService.Payment payment, double amount,
                                     String paymentType, Throwable error, CompletableFuture<CreationResult> result) {
        Throwable root = unwrap(error);
        if (operation.paymentState() == PaymentState.CHARGED) {
            boolean refunded = false;
            try { refunded = payment.refund(); } catch (Throwable refundError) { root.addSuppressed(refundError); }
            operation.payment(refunded ? PaymentState.REFUNDED : PaymentState.REFUND_PENDING,
                    owner.getUuid(), paymentType, amount);
        }
        operation.error(root);
        if (plugin.getCityStateManager().isValid(draft)) plugin.getCityStateManager().unloadCityState(draft);
        worlds.cleanupProvisionedWorld(draft, operation).whenComplete((ignored, cleanupError) -> {
            if (cleanupError == null) {
                deleteDraft(draft);
                if (operation.paymentState() != PaymentState.REFUND_PENDING) {
                    operation.complete();
                    operations.remove(operation);
                }
            } else {
                operation.error(unwrap(cleanupError));
            }
            release(owner, name);
            result.complete(CreationResult.failed(message(root)));
        });
    }

    private void cleanRequests(CityState cityState) {
        for (Request request : cityState.getMembers().stream()
                .flatMap(member -> member.getReceivedRequests().stream()).distinct().toList()) safeDelete(request);
        cityState.getSentRequests().stream().toList().forEach(this::safeDelete);
        cityState.getReceivedRequests().stream().toList().forEach(this::safeDelete);
    }

    private void safeDelete(Request request) {
        try { request.delete(); }
        catch (RuntimeException error) { PluginLogger.warning("清理解散城邦请求失败 " + request.getUuid() + ": " + error.getMessage()); }
    }

    private void deleteDraft(CityState cityState) {
        try { Files.deleteIfExists(cityState.getFile().toPath()); }
        catch (IOException error) { PluginLogger.warning("清理城邦草稿失败 " + cityState.getFile().getName() + ": " + error.getMessage()); }
    }

    private void runMain(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) runnable.run(); else Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private <T> CompletableFuture<T> runMainFuture(java.util.concurrent.Callable<T> callable) {
        CompletableFuture<T> result = new CompletableFuture<>();
        runMain(() -> {
            try { result.complete(callable.call()); } catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) current = current.getCause();
        return current;
    }

    private static String message(Throwable error) {
        Throwable root = unwrap(error);
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
