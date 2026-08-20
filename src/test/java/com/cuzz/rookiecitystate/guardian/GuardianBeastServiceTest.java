package com.cuzz.rookiecitystate.guardian;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.world.CityLifecycleState;
import com.cuzz.rookiecitystate.world.CityWorldState;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

final class GuardianBeastServiceTest {
    private ServerMock server;
    private RookieCityState plugin;
    private PlayerMock player;
    private CityStatePlayer playerData;
    private CityState city;
    private GuardianBeastService service;
    private AtomicLong now;

    @BeforeEach void setUp() {
        server = MockBukkit.mock();
        PluginMock vault = MockBukkit.createMockPlugin("Vault");
        MockBukkit.createMockPlugin("BKCommonLib");
        MockBukkit.createMockPlugin("My_Worlds");
        MockBukkit.createMockPlugin("FastAsyncWorldEdit");
        MockBukkit.createMockPlugin("WorldGuard");
        MockBukkit.createMockPlugin("RookieRegions");
        MockBukkit.createMockPlugin("ModelEngine");
        server.getServicesManager().register(Economy.class, economy(), vault, org.bukkit.plugin.ServicePriority.Normal);
        plugin = MockBukkit.load(RookieCityState.class);
        player = server.addPlayer("GuardianOwner");
        playerData = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        plugin.getCityStateManager().createCityState(playerData, "GuardianTest");
        city = playerData.getCityState();
        city.transitionWorld(CityLifecycleState.ACTIVE, CityWorldState.READY, null);
        World world = server.addSimpleWorld(city.getWorldName());
        player.teleport(world.getSpawnLocation());
        now = new AtomicLong(ZonedDateTime.of(2026, 8, 15, 3, 59, 59, 0,
                ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());
        service = new GuardianBeastService(plugin, GuardianBeastCoreTest.config(2), new Random(3), now::get);
        service.setVisualService(new ReadyVisual());
        assertEquals(SpeciesSelectionResult.Status.SUCCESS,
                service.selectSpecies(player, city, GuardianSpecies.FIRE).status());
    }

    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test void mainHandConsumesExactlyOneAndGlobalDailyLimitIsFive() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.COD, 6));
        for (int index = 0; index < 5; index++) {
            FeedResult result = service.feed(player, city, EquipmentSlot.HAND).toCompletableFuture().join();
            assertEquals(FeedResult.Status.SUCCESS, result.status());
        }
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        FeedResult sixth = service.feed(player, city, EquipmentSlot.HAND).toCompletableFuture().join();
        assertEquals(FeedResult.Status.DAILY_LIMIT, sixth.status());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(5, service.playerContribution(playerData)[2]);
    }

    @Test void offHandIsIgnoredWithoutConsumingAnything() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.SALMON, 2));
        FeedResult result = service.feed(player, city, EquipmentSlot.OFF_HAND).toCompletableFuture().join();
        assertEquals(FeedResult.Status.INVALID_HAND, result.status());
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(0, service.playerContribution(playerData)[2]);
    }

    @Test void globalFeedLimitResetsExactlyAtConfiguredBoundary() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.COD, 6));
        for (int index = 0; index < 5; index++) {
            assertEquals(FeedResult.Status.SUCCESS,
                    service.feed(player, city, EquipmentSlot.HAND).toCompletableFuture().join().status());
        }
        assertEquals(FeedResult.Status.DAILY_LIMIT,
                service.feed(player, city, EquipmentSlot.HAND).toCompletableFuture().join().status());
        now.addAndGet(2_000L);
        assertEquals(FeedResult.Status.SUCCESS,
                service.feed(player, city, EquipmentSlot.HAND).toCompletableFuture().join().status());
        assertEquals(1, service.playerContribution(playerData)[2]);
    }

    @Test void citySaveFailureRestoresItemAndPlayerContribution() throws Exception {
        player.getInventory().setItemInMainHand(new ItemStack(Material.PUFFERFISH, 2));
        GuardianBeastState state = service.state(city);
        Files.delete(state.file().toPath());
        Files.createDirectory(state.file().toPath());
        FeedResult result = service.feed(player, city, EquipmentSlot.HAND).toCompletableFuture().join();
        assertEquals(FeedResult.Status.SAVE_FAILED, result.status());
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(0, service.playerContribution(playerData)[0]);
        assertEquals(0, service.playerContribution(playerData)[1]);
        assertEquals(0, service.playerContribution(playerData)[2]);
    }

    @Test void visualFailureDoesNotRollbackPersistedGrowth() {
        service.setVisualService(new FailingVisual());
        GuardianBeastState state = service.state(city);
        state.ensureDay(service.getConfig().day(now.get()), service.getConfig(), new Random(1));
        state.setCompletedDays(2);
        state.yaml().set("daily.fullness", 19);
        state.save();
        player.getInventory().setItemInMainHand(new ItemStack(Material.COD, 2));
        FeedResult result = service.feed(player, city, EquipmentSlot.HAND).toCompletableFuture().join();
        assertEquals(FeedResult.Status.SUCCESS, result.status());
        assertEquals(1, result.newLevel());
        assertEquals(3, state.completedDays());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertNotNull(state.visualError());
    }

    @Test void blueprintInstallerCopiesOnlyMissingFilesAndNeverOverwrites() throws Exception {
        GuardianBlueprintInstaller installer = new GuardianBlueprintInstaller(plugin, new RegisteredModels());
        GuardianModelInstallStatus first = installer.installMissing();
        assertTrue(first.assetsValid(), first.errors().toString());
        assertTrue(first.modelsRegistered());
        assertEquals(7, first.installedFiles());
        var modelEngine = server.getPluginManager().getPlugin("ModelEngine");
        assertNotNull(modelEngine);
        var egg = modelEngine.getDataFolder().toPath()
                .resolve("blueprints/rookiecitystate/r1/rcs_guardian_egg_r1.bbmodel");
        Files.writeString(egg, "{}");
        GuardianModelInstallStatus second = installer.installMissing();
        assertFalse(second.assetsValid());
        assertEquals(0, second.installedFiles());
        assertEquals("{}", Files.readString(egg));
        assertTrue(plugin.getDataFolder().toPath().resolve("data/guardian_models/r1-manifest.yml").toFile().isFile());
    }

    private Economy economy() {
        return (Economy) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Economy.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "GuardianTestEconomy";
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
        if (type == float.class) return 0F;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return null;
    }

    private static final class ReadyVisual implements GuardianVisualService {
        @Override public boolean isAvailable() { return true; }
        @Override public String unavailableReason() { return null; }
        @Override public CompletionStage<Void> ensureVisual(CityState cityState) { return CompletableFuture.completedFuture(null); }
        @Override public CompletionStage<Void> updateVisual(CityState cityState, GuardianForm previous, GuardianForm next) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FailingVisual implements GuardianVisualService {
        @Override public boolean isAvailable() { return true; }
        @Override public String unavailableReason() { return null; }
        @Override public CompletionStage<Void> ensureVisual(CityState cityState) {
            return CompletableFuture.failedFuture(new IllegalStateException("visual failed"));
        }
        @Override public CompletionStage<Void> updateVisual(CityState cityState, GuardianForm previous, GuardianForm next) {
            return CompletableFuture.failedFuture(new IllegalStateException("visual failed"));
        }
    }

    private static final class RegisteredModels implements GuardianModelBackend {
        @Override public boolean isRegistered(String modelId) { return true; }
        @Override public ModelHandle create(org.bukkit.entity.ArmorStand base, String modelId) {
            throw new UnsupportedOperationException();
        }
        @Override public void playAnimation(ModelHandle handle, String animation, double lerpIn,
                                            double lerpOut, double speed, boolean force) { }
        @Override public void destroy(ModelHandle handle) { }
    }
}
