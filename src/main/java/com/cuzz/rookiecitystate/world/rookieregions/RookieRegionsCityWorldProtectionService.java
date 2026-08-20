package com.cuzz.rookiecitystate.world.rookieregions;

import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.world.CityWorldProtectionService;
import io.github.rookiecuzz.rookieregions.api.ApiCapability;
import io.github.rookiecuzz.rookieregions.api.ApiVersion;
import io.github.rookiecuzz.rookieregions.api.RookieRegionsApi;
import io.github.rookiecuzz.rookieregions.bukkit.BukkitWorlds;
import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionDomain;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.RegionShape;
import io.github.rookiecuzz.rookieregions.mutation.ConfirmationOption;
import io.github.rookiecuzz.rookieregions.mutation.RegionDeleteOutcome;
import io.github.rookiecuzz.rookieregions.mutation.RegionDeleteStatus;
import io.github.rookiecuzz.rookieregions.mutation.RegionFingerprints;
import io.github.rookiecuzz.rookieregions.mutation.RegionMutationApi;
import io.github.rookiecuzz.rookieregions.mutation.RegionSaveOutcome;
import io.github.rookiecuzz.rookieregions.mutation.RegionSaveRequest;
import io.github.rookiecuzz.rookieregions.mutation.RegionSaveRequests;
import io.github.rookiecuzz.rookieregions.mutation.SaveChoice;
import io.github.rookiecuzz.rookieregions.rule.FlagValue;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import io.github.rookiecuzz.rookieregions.rule.State;
import io.github.rookiecuzz.rookieregions.rule.Subject;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * RookieRegions-backed protection lifecycle for independently copied city worlds.
 *
 * <p>World folders are still provisioned by {@code CityWorldBackend}. RookieRegions owns the
 * persistent region documents and reacts to Bukkit world load/unload events itself. This adapter
 * creates a fresh native region graph for every copied world UUID and removes it before permanent
 * archive/deletion.</p>
 */
public final class RookieRegionsCityWorldProtectionService implements CityWorldProtectionService {
    public static final String AREA_REGION_ID = "city_area";

    private static final ApiVersion REQUIRED_API = new ApiVersion(2, 0, 0);
    private static final int MAX_STALE_RETRIES = 4;
    private static final int MAX_GLOBAL_WAIT_TICKS = 100;
    private static final Subject SYSTEM_SUBJECT = new Subject(
            null, Set.of(), Set.of("rookieregions.admin")
    );

    private final RookieRegionsApi regions;
    private final RegionMutationApi mutations;
    private final Plugin consumer;
    private volatile TemplateRegion templateRegion;

    public static RookieRegionsCityWorldProtectionService fromServices(Plugin consumer) {
        Objects.requireNonNull(consumer, "consumer plugin cannot be null");
        RookieRegionsApi regions = Bukkit.getServicesManager().load(RookieRegionsApi.class);
        RegionMutationApi mutations = Bukkit.getServicesManager().load(RegionMutationApi.class);
        if (regions == null || mutations == null) {
            throw new IllegalStateException("RookieRegions API services are unavailable");
        }
        if (!regions.version().isCompatibleWith(REQUIRED_API)) {
            throw new IllegalStateException("RookieRegions API " + REQUIRED_API
                    + "+ is required, found " + regions.version());
        }
        for (ApiCapability capability : List.of(
                ApiCapability.SNAPSHOT_QUERY,
                ApiCapability.TYPED_FLAGS,
                ApiCapability.ATOMIC_MUTATIONS
        )) {
            if (!regions.supports(capability)) {
                throw new IllegalStateException("RookieRegions capability is unavailable: " + capability);
            }
        }
        return new RookieRegionsCityWorldProtectionService(regions, mutations, consumer);
    }

    public RookieRegionsCityWorldProtectionService(RookieRegionsApi regions,
                                                   RegionMutationApi mutations) {
        this(regions, mutations, null);
    }

    private RookieRegionsCityWorldProtectionService(RookieRegionsApi regions,
                                                    RegionMutationApi mutations,
                                                    Plugin consumer) {
        this.regions = Objects.requireNonNull(regions, "RookieRegions API cannot be null");
        this.mutations = Objects.requireNonNull(mutations, "RookieRegions mutation API cannot be null");
        this.consumer = consumer;
    }

    @Override
    public CompletionStage<Void> ensureTemplateCore(World template, String coreRegionId,
                                                    int minX, int minY, int minZ,
                                                    int maxX, int maxY, int maxZ) {
        requirePrimaryThread("template region creation");
        WorldId world = BukkitWorlds.id(template);
        RegionKey key = new RegionKey(world, coreRegionId);
        return awaitGlobal(world, 0).thenCompose(globalKey -> {
            if (regions.snapshot().graph().region(key).isPresent()) {
                return CompletableFuture.completedFuture(null);
            }
            Region core = protectedRegionBuilder(
                    key,
                    new CuboidShape(minX, minY, minZ, inclusiveUpper(maxX),
                            inclusiveUpper(maxY), inclusiveUpper(maxZ))
            ).parent(globalKey).priority(20).build();
            return upsert(core, globalKey, "ensure-template-core", 0);
        });
    }

    @Override
    public void captureTemplate(World template, String coreRegionId) {
        requirePrimaryThread("template region capture");
        RegionKey key = new RegionKey(BukkitWorlds.id(template), coreRegionId);
        Region source = regions.snapshot().graph().region(key).orElseThrow(() ->
                new IllegalStateException("Template is missing RookieRegions region: " + coreRegionId)
        );
        RegionKey globalKey = RegionKey.global(key.world());
        if (source.parent().filter(globalKey::equals).isEmpty()) {
            throw new IllegalStateException("Template core region must be a direct child of __global__");
        }
        if (!source.owners().isEmpty() || !source.members().isEmpty()) {
            throw new IllegalStateException("Template core region must not have members or owners");
        }
        if (!source.shape().bounds().isFinite()) {
            throw new IllegalStateException("Template core region must have finite geometry");
        }
        templateRegion = TemplateRegion.capture(source);
    }

    @Override
    public CompletionStage<Void> installTemplateSnapshot(World snapshot) {
        requirePrimaryThread("template snapshot region installation");
        TemplateRegion captured = requireTemplate();
        WorldId world = BukkitWorlds.id(snapshot);
        return awaitGlobal(world, 0).thenCompose(globalKey -> {
            Region region = captured.instantiate(world, globalKey, captured.priority());
            return upsert(region, globalKey, "install-template-snapshot", 0);
        });
    }

    @Override
    public boolean hasTemplateSnapshot() {
        return templateRegion != null;
    }

    @Override
    public CompletionStage<Void> apply(CityState cityState, World world, int borderSize) {
        requirePrimaryThread("city region installation");
        Objects.requireNonNull(cityState, "city state cannot be null");
        TemplateRegion captured = requireTemplate();
        WorldId worldId = BukkitWorlds.id(world);

        double half = borderSize / 2.0D;
        double centerX = world.getWorldBorder().getCenter().getX();
        double centerZ = world.getWorldBorder().getCenter().getZ();
        RegionKey areaKey = new RegionKey(worldId, AREA_REGION_ID);
        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight();
        return awaitGlobal(worldId, 0).thenCompose(globalKey -> {
            Region area = Region.builder(areaKey, new CuboidShape(
                            Math.floor(centerX - half), minHeight, Math.floor(centerZ - half),
                            Math.ceil(centerX + half), maxHeight, Math.ceil(centerZ + half)))
                    .parent(globalKey)
                    .owners(owners(cityState))
                    .members(members(cityState))
                    .flag(ProtectionFlags.PVP, State.DENY)
                    .flag(ProtectionFlags.EXPLOSION, State.DENY)
                    .build();

            int corePriority = Math.max(captured.priority(), area.priority() + 10);
            RegionKey coreKey = new RegionKey(worldId, captured.id());
            Region core = captured.instantiate(worldId, areaKey, corePriority);

            Region tree = wishTreeRegion(worldId, coreKey, corePriority, area.priority());

            return upsert(area, globalKey, "install-city-area", 0)
                    .thenCompose(ignored -> upsert(core, areaKey, "install-city-core", 0))
                    .thenCompose(ignored -> upsert(tree, coreKey, "install-wish-tree", 0));
        });
    }

    @Override
    public CompletionStage<Void> synchronizeMembers(CityState cityState) {
        requirePrimaryThread("city region membership synchronization");
        Objects.requireNonNull(cityState, "city state cannot be null");
        World world = Bukkit.getWorld(cityState.getWorldName());
        Region current = cityArea(cityState, world);
        RegionKey key = current.key();
        Region.Builder changed = Region.builder(current.key(), current.shape())
                .priority(current.priority())
                .owners(owners(cityState))
                .members(members(cityState));
        current.parent().ifPresent(changed::parent);
        current.flags().values().forEach(changed::flagValue);
        RegionKey coreKey = new RegionKey(key.world(), requireTemplate().id());
        Region core = regions.snapshot().graph().region(coreKey).orElseThrow(() ->
                new IllegalStateException("Managed world is missing " + coreKey.id() + ": " + world.getName())
        );
        Region tree = wishTreeRegion(key.world(), coreKey, core.priority(), current.priority());
        return upsert(changed.build(), current.parent().orElse(null), "synchronize-city-members", 0)
                .thenCompose(ignored -> upsert(tree, coreKey, "synchronize-wish-tree-policy", 0))
                .thenRun(() -> {
                    if (!membersSynchronized(cityState)) {
                        throw new IllegalStateException("RookieRegions member readback did not match the city roster");
                    }
                });
    }

    @Override
    public boolean membersSynchronized(CityState cityState) {
        try {
            Region current = cityArea(cityState, Bukkit.getWorld(cityState.getWorldName()));
            return current.owners().players().equals(owners(cityState).players())
                    && current.owners().groups().equals(owners(cityState).groups())
                    && current.members().players().equals(members(cityState).players())
                    && current.members().groups().equals(members(cityState).groups());
        } catch (RuntimeException error) {
            return false;
        }
    }

    private Region cityArea(CityState cityState, World loadedWorld) {
        RegionSnapshot snapshot = regions.snapshot();
        if (loadedWorld != null) {
            RegionKey key = new RegionKey(BukkitWorlds.id(loadedWorld), AREA_REGION_ID);
            return snapshot.graph().region(key).orElseThrow(() ->
                    new IllegalStateException("Managed world is missing " + AREA_REGION_ID + ": " + loadedWorld.getName()));
        }
        String expectedWorld = "minecraft:" + cityState.getWorldName().trim().toLowerCase(java.util.Locale.ROOT);
        return snapshot.graph().regions().stream()
                .filter(region -> region.key().id().equals(AREA_REGION_ID))
                .filter(region -> region.key().world().namespacedKey().equals(expectedWorld))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Managed world is missing " + AREA_REGION_ID + ": " + cityState.getWorldName()));
    }

    @Override
    public CompletionStage<Void> removeWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("world name cannot be blank"));
        }
        String expectedKey = "minecraft:" + worldName.trim().toLowerCase(java.util.Locale.ROOT);
        RegionSnapshot snapshot = regions.snapshot();
        List<RegionKey> keys = snapshot.graph().regions().stream()
                .map(Region::key)
                .filter(key -> !key.isGlobal())
                .filter(key -> key.world().namespacedKey().equals(expectedKey))
                .sorted(Comparator.<RegionKey>comparingInt(key -> depth(snapshot, key)).reversed()
                        .thenComparing(RegionKey::compareTo))
                .toList();

        CompletionStage<Void> result = CompletableFuture.completedFuture(null);
        for (RegionKey key : keys) {
            result = result.thenCompose(ignored -> delete(key, 0));
        }
        return result;
    }

    private CompletionStage<Void> upsert(Region candidate, RegionKey expectedParent,
                                         String operation, int retry) {
        RegionSnapshot snapshot = regions.snapshot();
        Region current = snapshot.graph().region(candidate.key()).orElse(null);
        if (current != null && RegionFingerprints.region(current).equals(RegionFingerprints.region(candidate))) {
            return CompletableFuture.completedFuture(null);
        }
        String sessionId = "rookiecitystate:" + operation + ":" + UUID.randomUUID();
        RegionSaveRequest request = current == null
                ? RegionSaveRequests.create(snapshot, candidate).sessionId(sessionId).build()
                : RegionSaveRequests.edit(snapshot, current, candidate).sessionId(sessionId).build();
        return mutations.attemptSave(request, SYSTEM_SUBJECT).thenCompose(outcome ->
                handleSave(outcome, snapshot, current, candidate, expectedParent, sessionId, operation, retry)
        );
    }

    private CompletionStage<Void> handleSave(RegionSaveOutcome outcome,
                                             RegionSnapshot snapshot,
                                             Region current,
                                             Region candidate,
                                             RegionKey expectedParent,
                                             String sessionId,
                                             String operation,
                                             int retry) {
        if (outcome instanceof RegionSaveOutcome.Saved) {
            return CompletableFuture.completedFuture(null);
        }
        if (outcome instanceof RegionSaveOutcome.Stale stale) {
            return retrySave(candidate, expectedParent, operation, retry,
                    "stale RookieRegions snapshot: " + stale.reason());
        }
        if (outcome instanceof RegionSaveOutcome.ConfirmationRequired confirmation) {
            ConfirmationOption selected = selectConfirmation(confirmation.options(), expectedParent);
            RegionSaveRequest confirmed = current == null
                    ? RegionSaveRequests.create(snapshot, candidate).sessionId(sessionId)
                    .confirmationToken(selected.token()).build()
                    : RegionSaveRequests.edit(snapshot, current, candidate).sessionId(sessionId)
                    .confirmationToken(selected.token()).build();
            return mutations.attemptSave(confirmed, SYSTEM_SUBJECT).thenCompose(second -> {
                if (second instanceof RegionSaveOutcome.Saved) {
                    return CompletableFuture.completedFuture(null);
                }
                if (second instanceof RegionSaveOutcome.Stale stale) {
                    return retrySave(candidate, expectedParent, operation, retry,
                            "stale confirmed RookieRegions snapshot: " + stale.reason());
                }
                return CompletableFuture.failedFuture(saveFailure(operation, second));
            });
        }
        return CompletableFuture.failedFuture(saveFailure(operation, outcome));
    }

    private CompletionStage<Void> retrySave(Region candidate, RegionKey expectedParent,
                                            String operation, int retry, String message) {
        if (retry >= MAX_STALE_RETRIES) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    operation + " failed after snapshot retries: " + message
            ));
        }
        return upsert(candidate, expectedParent, operation, retry + 1);
    }

    private ConfirmationOption selectConfirmation(List<ConfirmationOption> options,
                                                  RegionKey expectedParent) {
        if (expectedParent != null) {
            return options.stream()
                    .filter(option -> option.option().choice() == SaveChoice.SET_PARENT)
                    .filter(option -> option.option().parent().filter(expectedParent::equals).isPresent())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "RookieRegions did not offer the required parent " + expectedParent
                    ));
        }
        return options.stream()
                .filter(option -> option.option().choice() == SaveChoice.KEEP_OVERLAP)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "RookieRegions requested an unexpected placement confirmation"
                ));
    }

    private CompletionStage<Void> delete(RegionKey key, int retry) {
        return mutations.delete(key, SYSTEM_SUBJECT).thenCompose(outcome -> {
            if (outcome.status() == RegionDeleteStatus.DELETED
                    || outcome.status() == RegionDeleteStatus.NOT_FOUND) {
                return CompletableFuture.completedFuture(null);
            }
            if ((outcome.status() == RegionDeleteStatus.STALE
                    || outcome.status() == RegionDeleteStatus.HAS_CHILDREN)
                    && retry < MAX_STALE_RETRIES) {
                return delete(key, retry + 1);
            }
            return CompletableFuture.failedFuture(deleteFailure(key, outcome));
        });
    }

    private IllegalStateException saveFailure(String operation, RegionSaveOutcome outcome) {
        if (outcome instanceof RegionSaveOutcome.Rejected rejected) {
            return new IllegalStateException(operation + " denied by RookieRegions ("
                    + rejected.reason() + "): " + rejected.message());
        }
        if (outcome instanceof RegionSaveOutcome.Failed failed) {
            return new IllegalStateException(operation + " storage failure: " + failed.message(), failed.cause());
        }
        if (outcome instanceof RegionSaveOutcome.ConfirmationRequired) {
            return new IllegalStateException(operation + " confirmation was not accepted");
        }
        if (outcome instanceof RegionSaveOutcome.Stale stale) {
            return new IllegalStateException(operation + " used a stale snapshot: " + stale.message());
        }
        return new IllegalStateException(operation + " returned an unknown RookieRegions outcome");
    }

    private IllegalStateException deleteFailure(RegionKey key, RegionDeleteOutcome outcome) {
        Throwable cause = outcome.cause().orElse(null);
        return new IllegalStateException("Unable to remove RookieRegions region " + key + " ("
                + outcome.status() + "): " + outcome.message(), cause);
    }

    private Region.Builder protectedRegionBuilder(RegionKey key, RegionShape shape) {
        return Region.builder(key, shape)
                .flag(ProtectionFlags.BUILD, State.DENY)
                .flag(ProtectionFlags.USE, State.DENY)
                .flag(ProtectionFlags.CONTAINER, State.DENY)
                .flag(ProtectionFlags.EXPLOSION, State.DENY);
    }

    private Region wishTreeRegion(WorldId world, RegionKey coreKey, int corePriority, int areaPriority) {
        RegionKey treeKey = new RegionKey(world, MainSettings.getWishTreeRegionId());
        Region.Builder builder = Region.builder(treeKey, new CuboidShape(
                        MainSettings.getWishTreeOriginX() - 7,
                        MainSettings.getWishTreeOriginY(),
                        MainSettings.getWishTreeOriginZ() - 7,
                        MainSettings.getWishTreeOriginX() + 8,
                        MainSettings.getWishTreeOriginY() + 24,
                        MainSettings.getWishTreeOriginZ() + 8))
                .parent(coreKey)
                .priority(Math.max(corePriority + 10, areaPriority + 20));
        return applyWishTreePolicy(builder).build();
    }

    static Region.Builder applyWishTreePolicy(Region.Builder builder) {
        return builder
                .flag(ProtectionFlags.BUILD, State.DENY)
                .flag(ProtectionFlags.USE, State.ALLOW)
                .flag(ProtectionFlags.CONTAINER, State.DENY)
                .flag(ProtectionFlags.EXPLOSION, State.DENY);
    }

    private RegionDomain owners(CityState cityState) {
        return RegionDomain.builder().player(cityState.getOwner().getUuid()).build();
    }

    private RegionDomain members(CityState cityState) {
        RegionDomain.Builder members = RegionDomain.builder();
        UUID owner = cityState.getOwner().getUuid();
        for (CityStateMember member : cityState.getMembers()) {
            if (!member.getUuid().equals(owner)) {
                members.player(member.getUuid());
            }
        }
        return members.build();
    }

    private TemplateRegion requireTemplate() {
        TemplateRegion captured = templateRegion;
        if (captured == null) {
            throw new IllegalStateException("Template core region has not been captured");
        }
        return captured;
    }

    private CompletionStage<RegionKey> awaitGlobal(WorldId world, int elapsedTicks) {
        RegionKey globalKey = RegionKey.global(world);
        if (regions.snapshot().graph().region(globalKey).isPresent()) {
            return CompletableFuture.completedFuture(globalKey);
        }
        if (consumer == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "RookieRegions has not published the global region for " + world.namespacedKey()
            ));
        }
        if (elapsedTicks >= MAX_GLOBAL_WAIT_TICKS || !consumer.isEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Timed out waiting for RookieRegions global region in " + world.namespacedKey()
            ));
        }
        CompletableFuture<RegionKey> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskLater(consumer, () ->
                awaitGlobal(world, elapsedTicks + 1).whenComplete((key, error) -> {
                    if (error == null) result.complete(key);
                    else result.completeExceptionally(error);
                }), 1L);
        return result;
    }

    private int depth(RegionSnapshot snapshot, RegionKey key) {
        int depth = 0;
        Region current = snapshot.graph().region(key).orElse(null);
        while (current != null && current.parent().isPresent()) {
            depth++;
            current = snapshot.graph().region(current.parent().orElseThrow()).orElse(null);
        }
        return depth;
    }

    private double inclusiveUpper(int coordinate) {
        return Math.addExact(coordinate, 1);
    }

    private void requirePrimaryThread(String operation) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(operation + " must start on the server thread");
        }
    }

    private record TemplateRegion(String id, RegionShape shape, int priority,
                                  Map<String, FlagValue<?>> flags) {
        private TemplateRegion {
            flags = Map.copyOf(flags);
        }

        static TemplateRegion capture(Region source) {
            return new TemplateRegion(source.key().id(), source.shape(), source.priority(), source.flags());
        }

        Region instantiate(WorldId world, RegionKey parent, int requestedPriority) {
            RegionKey key = new RegionKey(world, id);
            Region.Builder builder = Region.builder(key, shape).priority(requestedPriority);
            if (parent != null) {
                builder.parent(parent);
            }
            Map<String, FlagValue<?>> effective = new LinkedHashMap<>(flags);
            effective.put(ProtectionFlags.BUILD.name(), ProtectionFlags.BUILD.value(State.DENY));
            effective.put(ProtectionFlags.USE.name(), ProtectionFlags.USE.value(State.DENY));
            effective.put(ProtectionFlags.CONTAINER.name(), ProtectionFlags.CONTAINER.value(State.DENY));
            effective.put(ProtectionFlags.EXPLOSION.name(), ProtectionFlags.EXPLOSION.value(State.DENY));
            effective.values().forEach(builder::flagValue);
            return builder.build();
        }
    }
}
