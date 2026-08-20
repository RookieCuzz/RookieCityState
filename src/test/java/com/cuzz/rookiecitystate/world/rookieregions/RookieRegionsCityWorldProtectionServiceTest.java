package com.cuzz.rookiecitystate.world.rookieregions;

import io.github.rookiecuzz.rookieregions.api.ApiCapability;
import io.github.rookiecuzz.rookieregions.api.ApiVersion;
import io.github.rookiecuzz.rookieregions.api.ModuleBindingQuery;
import io.github.rookiecuzz.rookieregions.api.ProtectionQuery;
import io.github.rookiecuzz.rookieregions.api.RookieRegionsApi;
import io.github.rookiecuzz.rookieregions.bukkit.BukkitWorlds;
import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionQuery;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.mutation.RegionDeleteOutcome;
import io.github.rookiecuzz.rookieregions.mutation.RegionDeleteStatus;
import io.github.rookiecuzz.rookieregions.mutation.RegionMutationApi;
import io.github.rookiecuzz.rookieregions.mutation.RegionSaveOutcome;
import io.github.rookiecuzz.rookieregions.mutation.RegionSaveRequest;
import io.github.rookiecuzz.rookieregions.mutation.SaveChoice;
import io.github.rookiecuzz.rookieregions.provider.RegionProvider;
import io.github.rookiecuzz.rookieregions.rule.FlagRegistry;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import io.github.rookiecuzz.rookieregions.rule.State;
import io.github.rookiecuzz.rookieregions.rule.Subject;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RookieRegionsCityWorldProtectionServiceTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void templateRegionsAreProtectedCopiedIdempotentlyAndDeletedChildFirst() {
        FakeRegions fake = new FakeRegions();
        RookieRegionsCityWorldProtectionService service =
                new RookieRegionsCityWorldProtectionService(fake, fake);
        World template = worldWithKey(server.addSimpleWorld("rr_template"));
        fake.addWorld(template);

        service.ensureTemplateCore(template, "city_core", -8, -64, -6, 8, 319, 6)
                .toCompletableFuture().join();
        RegionKey templateKey = new RegionKey(BukkitWorlds.id(template), "city_core");
        Region core = fake.snapshot().graph().region(templateKey).orElseThrow();
        assertEquals(9.0D, core.shape().bounds().maxX());
        assertEquals(320.0D, core.shape().bounds().maxY());
        assertEquals(7.0D, core.shape().bounds().maxZ());
        assertEquals(State.DENY, core.flag(ProtectionFlags.BUILD).orElseThrow().value());
        assertEquals(State.DENY, core.flag(ProtectionFlags.USE).orElseThrow().value());
        assertEquals(State.DENY, core.flag(ProtectionFlags.CONTAINER).orElseThrow().value());

        long firstRevision = fake.snapshot().revision();
        service.ensureTemplateCore(template, "city_core", -8, -64, -6, 8, 319, 6)
                .toCompletableFuture().join();
        assertEquals(firstRevision, fake.snapshot().revision());

        service.captureTemplate(template, "city_core");
        World snapshotWorld = worldWithKey(server.addSimpleWorld("rr_snapshot"));
        fake.addWorld(snapshotWorld);
        service.installTemplateSnapshot(snapshotWorld).toCompletableFuture().join();
        RegionKey snapshotCore = new RegionKey(BukkitWorlds.id(snapshotWorld), "city_core");
        assertTrue(fake.snapshot().graph().region(snapshotCore).isPresent());

        RegionKey childKey = new RegionKey(BukkitWorlds.id(snapshotWorld), "nested_child");
        fake.publish(Region.builder(childKey, new CuboidShape(-2, 0, -2, 2, 10, 2))
                .parent(snapshotCore)
                .build());
        service.removeWorld(snapshotWorld.getName()).toCompletableFuture().join();

        assertFalse(fake.snapshot().graph().region(snapshotCore).isPresent());
        assertFalse(fake.snapshot().graph().region(childKey).isPresent());
        assertTrue(fake.snapshot().graph().region(templateKey).isPresent());
    }

    @Test
    void wishTreePolicyAllowsUseButKeepsDestructiveActionsDenied() {
        World world = worldWithKey(server.addSimpleWorld("wish_tree_policy"));
        RegionKey key = new RegionKey(BukkitWorlds.id(world), "city_wish_tree");
        Region tree = RookieRegionsCityWorldProtectionService.applyWishTreePolicy(
                Region.builder(key, new CuboidShape(0, 0, 0, 10, 10, 10))
        ).build();

        assertEquals(State.ALLOW, tree.flag(ProtectionFlags.USE).orElseThrow().value());
        assertEquals(State.DENY, tree.flag(ProtectionFlags.BUILD).orElseThrow().value());
        assertEquals(State.DENY, tree.flag(ProtectionFlags.CONTAINER).orElseThrow().value());
        assertEquals(State.DENY, tree.flag(ProtectionFlags.EXPLOSION).orElseThrow().value());
    }

    private World worldWithKey(World delegate) {
        return (World) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getKey") && method.getParameterCount() == 0) {
                        return NamespacedKey.minecraft(delegate.getName().toLowerCase(java.util.Locale.ROOT));
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }
        );
    }

    private static final class FakeRegions implements RookieRegionsApi, RegionMutationApi {
        private RegionSnapshot snapshot = RegionSnapshot.empty();

        @Override public ApiVersion version() { return ApiVersion.CURRENT; }
        @Override public Set<ApiCapability> capabilities() { return Set.of(
                ApiCapability.SNAPSHOT_QUERY, ApiCapability.TYPED_FLAGS, ApiCapability.ATOMIC_MUTATIONS
        ); }
        @Override public RegionSnapshot snapshot() { return snapshot; }
        @Override public RegionQuery query() { return new RegionQuery(snapshot); }
        @Override public ProtectionQuery protection() { return new ProtectionQuery(query()); }
        @Override public FlagRegistry flagRegistry() { return ProtectionFlags.REGISTRY; }
        @Override public RegionProvider nativeProvider() { throw new UnsupportedOperationException(); }
        @Override public Map<String, RegionProvider> providers() { return Map.of(); }
        @Override public Optional<RegionProvider> provider(String id) { return Optional.empty(); }
        @Override public ModuleBindingQuery moduleBindings() { return null; }

        @Override
        public CompletionStage<RegionSaveOutcome> attemptSave(RegionSaveRequest request, Subject subject) {
            Region candidate = request.candidate();
            ArrayList<Region> regions = new ArrayList<>(snapshot.graph().regions());
            regions.removeIf(region -> region.key().equals(candidate.key()));
            regions.add(candidate);
            snapshot = RegionSnapshot.of(snapshot.revision() + 1L, regions);
            return CompletableFuture.completedFuture(
                    new RegionSaveOutcome.Saved(snapshot, candidate, SaveChoice.DIRECT)
            );
        }

        @Override
        public CompletionStage<RegionDeleteOutcome> delete(RegionKey key, Subject subject) {
            Region current = snapshot.graph().region(key).orElse(null);
            if (current == null) {
                return CompletableFuture.completedFuture(new RegionDeleteOutcome(
                        RegionDeleteStatus.NOT_FOUND, Optional.of(snapshot), Optional.empty(), "", Optional.empty()
                ));
            }
            if (!snapshot.graph().children(key).isEmpty()) {
                return CompletableFuture.completedFuture(new RegionDeleteOutcome(
                        RegionDeleteStatus.HAS_CHILDREN, Optional.of(snapshot), Optional.empty(),
                        "region still has children", Optional.empty()
                ));
            }
            ArrayList<Region> regions = new ArrayList<>(snapshot.graph().regions());
            regions.removeIf(region -> region.key().equals(key));
            snapshot = RegionSnapshot.of(snapshot.revision() + 1L, regions);
            return CompletableFuture.completedFuture(new RegionDeleteOutcome(
                    RegionDeleteStatus.DELETED, Optional.of(snapshot), Optional.of(current), "", Optional.empty()
            ));
        }

        void publish(Region region) {
            ArrayList<Region> regions = new ArrayList<>(snapshot.graph().regions());
            regions.add(region);
            snapshot = RegionSnapshot.of(snapshot.revision() + 1L, regions);
        }

        void addWorld(World world) {
            publish(Region.builder(RegionKey.global(BukkitWorlds.id(world)), GlobalShape.INSTANCE)
                    .priority(Integer.MIN_VALUE)
                    .build());
        }
    }
}
