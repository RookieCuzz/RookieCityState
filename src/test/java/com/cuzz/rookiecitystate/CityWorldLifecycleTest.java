package com.cuzz.rookiecitystate;

import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.api.event.CityStateCreatedEvent;
import com.cuzz.rookiecitystate.api.event.CityStateDeletedEvent;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.transaction.TransactionService;
import com.cuzz.rookiecitystate.world.CityStateLifecycleService;
import com.cuzz.rookiecitystate.world.CityLifecycleState;
import com.cuzz.rookiecitystate.world.CityWorldBackend;
import com.cuzz.rookiecitystate.world.CityWorldProtectionService;
import com.cuzz.rookiecitystate.world.CityWorldService;
import com.cuzz.rookiecitystate.world.CityWorldState;
import com.cuzz.rookiecitystate.world.CreationResult;
import com.cuzz.rookiecitystate.world.DeletionResult;
import com.cuzz.rookiecitystate.world.WorldProvisionSpec;
import com.cuzz.rookiecitystate.world.WorldVisibility;
import com.cuzz.rookiecitystate.world.operation.WorldOperationStore;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CityWorldLifecycleTest {
    private ServerMock server;
    private RookieCityState plugin;
    private CityWorldService worlds;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        PluginMock vault = MockBukkit.createMockPlugin("Vault");
        MockBukkit.createMockPlugin("BKCommonLib");
        MockBukkit.createMockPlugin("My_Worlds");
        MockBukkit.createMockPlugin("FastAsyncWorldEdit");
        MockBukkit.createMockPlugin("WorldGuard");
        MockBukkit.createMockPlugin("RookieRegions");
        server.getServicesManager().register(Economy.class, economy(), vault, org.bukkit.plugin.ServicePriority.Normal);
        plugin = MockBukkit.load(RookieCityState.class);
        if (server.getWorld("world") == null) server.addSimpleWorld("world");
        World template = server.addSimpleWorld("citystate_template");
        template.getWorldBorder().setSize(512D);
    }

    @AfterEach
    void tearDown() {
        if (worlds != null) worlds.shutdown();
        MockBukkit.unmock();
    }

    @Test
    void creationChargesOnlyAfterWorldIsReady() throws Exception {
        FakeBackend backend = new FakeBackend(false);
        WorldOperationStore store = new WorldOperationStore(plugin);
        worlds = new CityWorldService(plugin, backend, new FakeProtection(), store);
        markTemplateReady(worlds);
        CityStateLifecycleService lifecycle = new CityStateLifecycleService(plugin, worlds, store);
        PlayerMock player = server.addPlayer("WorldOwner");
        CityStatePlayer owner = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        AtomicInteger charged = new AtomicInteger();
        AtomicInteger refunded = new AtomicInteger();
        AtomicInteger createdEvents = new AtomicInteger();
        class CreatedCapture implements Listener {
            @EventHandler public void onCreated(CityStateCreatedEvent event) {
                assertEquals(CityWorldState.READY, event.getCityState().getWorldState());
                createdEvents.incrementAndGet();
            }
        }
        server.getPluginManager().registerEvents(new CreatedCapture(), plugin);

        CompletionStage<CreationResult> stage = lifecycle.create(owner, "独立世界", payment(charged, refunded),
                "TEST", 100D);
        CreationResult result = await(stage);

        assertTrue(result.success(), result.reason());
        assertEquals(1, charged.get());
        assertEquals(0, refunded.get());
        assertEquals(1, createdEvents.get());
        assertEquals(CityWorldState.READY, result.cityState().getWorldState());
        assertSame(result.cityState(), plugin.getCityStateManager().getCityState(result.cityState().getUuid()));
        assertTrue(Files.isRegularFile(backend.worldFolder(result.cityState().getWorldName())
                .resolve(".rookiecitystate-world.yml")));
    }

    @Test
    void templateValidationCreatesOneImmutableRevisionSnapshot() throws Exception {
        FakeBackend backend = new FakeBackend(false);
        Files.createDirectories(backend.worldFolder("citystate_template"));
        WorldOperationStore store = new WorldOperationStore(plugin);
        worlds = new CityWorldService(plugin, backend, new FakeProtection(), store);

        assertTrue(await(worlds.validateTemplate()), worlds.getTemplateError());
        String snapshot = "rcs_template_" + MainSettings.getCityStateWorldTemplateRevision();
        assertTrue(Files.isRegularFile(backend.worldFolder(snapshot)
                .resolve(".rookiecitystate-template.yml")));
        assertEquals(1, backend.copyCount.get());

        assertTrue(await(worlds.validateTemplate()), worlds.getTemplateError());
        assertEquals(1, backend.copyCount.get(), "同一模板修订不得被重复覆盖");
    }

    @Test
    void copyFailureNeverChargesAndDoesNotRegisterDraft() throws Exception {
        FakeBackend backend = new FakeBackend(true);
        WorldOperationStore store = new WorldOperationStore(plugin);
        worlds = new CityWorldService(plugin, backend, new FakeProtection(), store);
        markTemplateReady(worlds);
        CityStateLifecycleService lifecycle = new CityStateLifecycleService(plugin, worlds, store);
        CityStatePlayer owner = plugin.getCityStatePlayerManager().getCityStatePlayer(server.addPlayer("FailedOwner"));
        AtomicInteger charged = new AtomicInteger();
        AtomicInteger refunded = new AtomicInteger();

        CreationResult result = await(lifecycle.create(owner, "失败世界", payment(charged, refunded), "TEST", 100D));

        assertFalse(result.success());
        assertEquals(0, charged.get());
        assertEquals(0, refunded.get());
        assertFalse(owner.isInCityState());
        assertNull(plugin.getCityStateManager().getCityStateByName("失败世界"));
        assertTrue(store.loadAll().isEmpty(), "清理成功的失败操作不应留待重启重复执行");
    }

    @Test
    void legacyYamlMigratesOnceAndKeepsBackup() throws Exception {
        PlayerMock ownerPlayer = server.addPlayer("LegacyOwner");
        CityStatePlayer owner = plugin.getCityStatePlayerManager().getCityStatePlayer(ownerPlayer);
        UUID id = UUID.randomUUID();
        java.io.File file = plugin.getCityStateManager().cityStateFile(id);
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("uuid", id.toString());
        yaml.set("name", "旧城邦");
        yaml.set("creation_time", System.currentTimeMillis());
        yaml.set("members." + owner.getUuid() + ".position", "OWNER");
        yaml.set("members." + owner.getUuid() + ".join_time", System.currentTimeMillis());
        YamlFiles.save(yaml, file);

        plugin.getCityStateManager().loadCityState(file);
        CityState migrated = plugin.getCityStateManager().getCityState(id);
        assertNotNull(migrated);
        assertEquals(CityWorldState.UNASSIGNED, migrated.getWorldState());
        assertEquals(2, YamlFiles.load(file).getInt("schema_version"));
        assertTrue(new java.io.File(file.getParentFile(), file.getName() + ".bak.v1").isFile());
    }

    @Test
    void atomicYamlRecoveryPromotesValidatedTemporaryFile() throws Exception {
        Path folder = plugin.getDataFolder().toPath().resolve("atomic-test");
        Files.createDirectories(folder);
        Path temporary = folder.resolve("state.yml.123456.tmp");
        Files.writeString(temporary, "value: 42\n", StandardCharsets.UTF_8);

        YamlFiles.recoverAtomicWrites(plugin.getDataFolder().toPath());

        Path target = folder.resolve("state.yml");
        assertFalse(Files.exists(temporary));
        assertEquals(42, YamlFiles.load(target.toFile()).getInt("value"));
    }

    @Test
    void futureSchemaIsQuarantinedInsteadOfLoaded() throws Exception {
        UUID id = UUID.randomUUID();
        java.io.File file = plugin.getCityStateManager().cityStateFile(id);
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("schema_version", 3);
        yaml.set("uuid", id.toString());
        yaml.set("name", "FutureCity");
        YamlFiles.save(yaml, file);

        plugin.getCityStateManager().loadCityStates();

        assertNull(plugin.getCityStateManager().getCityState(id));
        assertFalse(file.exists());
        Path quarantine = plugin.getDataFolder().toPath().resolve("data/quarantine/city_states");
        try (var files = Files.list(quarantine)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().contains(id + ".yml")));
        }
    }

    @Test
    void privateAndPublicAccessRulesUseLiveMembershipAndAdminPermission() {
        PlayerMock ownerPlayer = server.addPlayer("AccessOwner");
        CityStatePlayer owner = plugin.getCityStatePlayerManager().getCityStatePlayer(ownerPlayer);
        plugin.getCityStateManager().createCityState(owner, "AccessCity");
        CityState cityState = owner.getCityState();
        PlayerMock visitor = server.addPlayer("Visitor");

        assertTrue(plugin.getCityWorldService().canAccess(ownerPlayer, cityState));
        assertFalse(plugin.getCityWorldService().canAccess(visitor, cityState));

        cityState.setWorldVisibility(WorldVisibility.PUBLIC);
        assertTrue(plugin.getCityWorldService().canAccess(visitor, cityState));

        cityState.setWorldVisibility(WorldVisibility.PRIVATE);
        visitor.addAttachment(plugin, "rookiecitystate.admin", true);
        assertTrue(plugin.getCityWorldService().canAccess(visitor, cityState));
    }

    @Test
    void deletionArchivesWorldAndFiresEventOnlyAfterArchived() throws Exception {
        FakeBackend backend = new FakeBackend(false);
        WorldOperationStore store = new WorldOperationStore(plugin);
        worlds = new CityWorldService(plugin, backend, new FakeProtection(), store);
        markTemplateReady(worlds);
        CityStateLifecycleService lifecycle = new CityStateLifecycleService(plugin, worlds, store);
        CityState cityState = readyLegacyCity(worlds, "ArchivedCity");
        AtomicInteger deletedEvents = captureDeletedEvents();

        DeletionResult result = await(lifecycle.delete(cityState));

        assertTrue(result.success(), result.reason());
        assertEquals(1, deletedEvents.get());
        assertEquals(CityLifecycleState.DELETED, cityState.getLifecycleState());
        assertNull(plugin.getCityStateManager().getCityState(cityState.getUuid()));
        assertFalse(Files.exists(backend.worldFolder(cityState.getWorldName())));
        assertEquals(1, worlds.listArchives().size());
    }

    @Test
    void failedDeletionRemainsRecoverableAndDoesNotFireEarlyEvent() throws Exception {
        FakeBackend backend = new FakeBackend(false);
        WorldOperationStore store = new WorldOperationStore(plugin);
        worlds = new CityWorldService(plugin, backend, new FakeProtection(), store);
        markTemplateReady(worlds);
        CityStateLifecycleService lifecycle = new CityStateLifecycleService(plugin, worlds, store);
        CityState cityState = readyLegacyCity(worlds, "RetryDeleteCity");
        AtomicInteger deletedEvents = captureDeletedEvents();
        backend.failUnload = true;

        DeletionResult first = await(lifecycle.delete(cityState));
        assertFalse(first.success());
        assertEquals(CityLifecycleState.ERROR, cityState.getLifecycleState());
        assertEquals(0, deletedEvents.get());

        backend.failUnload = false;
        assertEquals(1, await(lifecycle.recover()));
        assertEquals(1, deletedEvents.get());
        assertNull(plugin.getCityStateManager().getCityState(cityState.getUuid()));
        assertEquals(1, worlds.listArchives().size());
    }

    private CityState readyLegacyCity(CityWorldService service, String name) throws Exception {
        PlayerMock ownerPlayer = server.addPlayer(name + "Owner");
        CityStatePlayer owner = plugin.getCityStatePlayerManager().getCityStatePlayer(ownerPlayer);
        plugin.getCityStateManager().createCityState(owner, name);
        CityState cityState = owner.getCityState();
        await(service.provisionLegacy(cityState));
        assertEquals(CityWorldState.READY, cityState.getWorldState());
        return cityState;
    }

    private AtomicInteger captureDeletedEvents() {
        AtomicInteger events = new AtomicInteger();
        class DeletedCapture implements Listener {
            @EventHandler public void onDeleted(CityStateDeletedEvent event) {
                assertEquals(CityWorldState.ARCHIVED, event.getCityState().getWorldState());
                events.incrementAndGet();
            }
        }
        server.getPluginManager().registerEvents(new DeletedCapture(), plugin);
        return events;
    }

    private TransactionService.Payment payment(AtomicInteger charged, AtomicInteger refunded) {
        return new TransactionService.Payment() {
            @Override public void validate() { }
            @Override public boolean charge() { charged.incrementAndGet(); return true; }
            @Override public boolean refund() { refunded.incrementAndGet(); return true; }
        };
    }

    private void markTemplateReady(CityWorldService service) throws Exception {
        java.lang.reflect.Field field = CityWorldService.class.getDeclaredField("templateReady");
        field.setAccessible(true);
        field.setBoolean(service, true);
    }

    private <T> T await(CompletionStage<T> stage) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!stage.toCompletableFuture().isDone() && System.currentTimeMillis() < deadline) {
            server.getScheduler().performOneTick();
            Thread.sleep(5L);
        }
        return stage.toCompletableFuture().get();
    }

    private final class FakeBackend implements CityWorldBackend {
        private final boolean failCopy;
        private final AtomicInteger copyCount = new AtomicInteger();
        private volatile boolean failUnload;

        private FakeBackend(boolean failCopy) { this.failCopy = failCopy; }
        @Override public String name() { return "FAKE"; }
        @Override public boolean available() { return true; }
        @Override public PreparedCopy prepareCopy(WorldProvisionSpec spec) {
            return () -> {
                if (failCopy) throw new IllegalStateException("injected copy failure");
                copyCount.incrementAndGet();
                Files.createDirectories(worldFolder(spec.targetWorld()));
            };
        }
        @Override public boolean exists(String worldName) { return Files.isDirectory(worldFolder(worldName)); }
        @Override public World load(String worldName) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) world = server.addSimpleWorld(worldName);
            world.getWorldBorder().setSize(512D);
            return world;
        }
        @Override public boolean unload(String worldName, boolean save) { return !failUnload; }
        @Override public void configureManagedWorld(String worldName, World world) { world.setPVP(false); }
        @Override public void save(World world) { }
        @Override public void forget(String worldName) { }
        @Override public Path worldFolder(String worldName) {
            return plugin.getDataFolder().toPath().resolve("fake-worlds").resolve(worldName).normalize();
        }
        @Override public Path worldRoot() {
            return plugin.getDataFolder().toPath().resolve("fake-worlds").toAbsolutePath().normalize();
        }
    }

    private static final class FakeProtection implements CityWorldProtectionService {
        private boolean captured;
        @Override public CompletionStage<Void> ensureTemplateCore(World template, String coreRegionId,
                                                                  int minX, int minY, int minZ,
                                                                  int maxX, int maxY, int maxZ) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public void captureTemplate(World template, String coreRegionId) { captured = true; }
        @Override public CompletionStage<Void> installTemplateSnapshot(World snapshot) {
            captured = true;
            return CompletableFuture.completedFuture(null);
        }
        @Override public boolean hasTemplateSnapshot() { return captured; }
        @Override public CompletionStage<Void> apply(CityState cityState, World world, int borderSize) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<Void> synchronizeMembers(CityState cityState) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<Void> removeWorld(String worldName) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private Economy economy() {
        return (Economy) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Economy.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "TestEconomy";
                    case "isEnabled", "has" -> true;
                    case "fractionalDigits" -> 2;
                    case "format" -> String.valueOf(args[0]);
                    case "currencyNamePlural", "currencyNameSingular" -> "coin";
                    case "getBalance" -> 100000D;
                    case "withdrawPlayer", "depositPlayer" -> new EconomyResponse(
                            ((Number) args[args.length - 1]).doubleValue(), 100000D,
                            EconomyResponse.ResponseType.SUCCESS, "");
                    case "hasAccount", "createPlayerAccount", "hasBankSupport" -> true;
                    case "getBanks" -> new ArrayList<String>();
                    default -> defaultValue(method.getReturnType());
                });
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == double.class) return 0D;
        if (type == long.class) return 0L;
        return 0;
    }
}
