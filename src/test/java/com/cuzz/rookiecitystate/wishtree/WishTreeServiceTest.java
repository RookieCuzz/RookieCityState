package com.cuzz.rookiecitystate.wishtree;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.world.CityLifecycleState;
import com.cuzz.rookiecitystate.world.CityWorldState;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WishTreeServiceTest {
    private ServerMock server;
    private RookieCityState plugin;
    private PlayerMock player;
    private CityStatePlayer playerData;
    private CityState city;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PluginMock vault = MockBukkit.createMockPlugin("Vault");
        MockBukkit.createMockPlugin("BKCommonLib");
        MockBukkit.createMockPlugin("My_Worlds");
        MockBukkit.createMockPlugin("FastAsyncWorldEdit");
        MockBukkit.createMockPlugin("WorldGuard");
        MockBukkit.createMockPlugin("RookieRegions");
        server.getServicesManager().register(Economy.class, economy(), vault,
                org.bukkit.plugin.ServicePriority.Normal);
        plugin = MockBukkit.load(RookieCityState.class);
        if (server.getWorld("world") == null) server.addSimpleWorld("world");
        player = server.addPlayer("Wisher");
        playerData = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        plugin.getCityStateManager().createCityState(playerData, "WishTest");
        city = playerData.getCityState();
        city.transitionWorld(CityLifecycleState.ACTIVE, CityWorldState.READY, null);
    }

    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test
    void oneFreeAndFivePaidWishesAreEnforcedAtomically() {
        WishTreeService service = service(0.99D);
        assertTrue(service.selectTarget(player, "diamond_target").success());
        service.grantStones(playerData, 10);
        for (int index = 0; index < 6; index++) assertTrue(service.wish(player, city).toCompletableFuture().join().success());
        WishResult seventh = service.wish(player, city).toCompletableFuture().join();
        assertFalse(seventh.success());
        WishPlayerAdminView view = service.getAdminView(playerData);
        assertEquals(1, view.freeUsed());
        assertEquals(5, view.paidUsed());
        assertEquals(5, view.magicStones());
        assertEquals(6, view.rarePity());
    }

    @Test
    void rareTargetIsGuaranteedOnTheThirtiethWish() {
        WishTreeService service = service(0.99D);
        assertTrue(service.selectTarget(player, "diamond_target").success());
        service.grantStones(playerData, 100);
        WishResult result = null;
        for (int index = 0; index < 30; index++) {
            if (index > 0 && index % 6 == 0) service.resetDaily(playerData);
            result = service.wish(player, city).toCompletableFuture().join();
            assertTrue(result.success());
            if (index < 29) assertFalse(result.targetAwarded());
        }
        assertNotNull(result);
        assertTrue(result.targetAwarded());
        assertEquals(0, service.getAdminView(playerData).rarePity());
    }

    @Test
    void earlyHitClearsOnlyTheSelectedQualityPity() {
        WishTreeService missService = service(0.99D);
        missService.grantStones(playerData, 10);
        assertTrue(missService.selectTarget(player, "diamond_target").success());
        assertFalse(missService.wish(player, city).toCompletableFuture().join().targetAwarded());
        assertEquals(1, missService.getAdminView(playerData).rarePity());

        WishTreeService hitService = service(0D);
        assertTrue(hitService.selectTarget(player, "netherite_target").success() == false,
                "epic targets stay locked on a level-one tree");
        assertTrue(hitService.selectTarget(player, "diamond_target").success());
        assertTrue(hitService.wish(player, city).toCompletableFuture().join().targetAwarded());
        assertEquals(0, hitService.getAdminView(playerData).rarePity());
    }

    @Test
    void fullMailboxBlocksWishBeforeConsumingTheFreeAttempt() {
        WishTreeService service = service(0.99D);
        assertTrue(service.selectTarget(player, "diamond_target").success());
        service.grantReward(playerData, "iron_bundle", 100, city.getUuid());
        service.grantReward(playerData, "iron_bundle", 100, city.getUuid());
        WishResult result = service.wish(player, city).toCompletableFuture().join();
        assertFalse(result.success());
        assertEquals(0, service.getAdminView(playerData).freeUsed());
    }

    @Test
    void interruptedDispatchBecomesAmbiguousInsteadOfBeingRepeated() {
        WishTreeService service = service(0.99D);
        UUID claimId = service.grantReward(playerData, "iron_bundle", 1, city.getUuid()).getFirst();
        playerData.getYaml().set("wish_tree.mailbox." + claimId + ".state", WishClaimState.DISPATCHING.name());
        playerData.save();
        assertEquals(1, service.getAdminView(playerData).ambiguousClaims());
        ClaimResult result = service.claim(player, claimId).toCompletableFuture().join();
        assertFalse(result.success());
        assertEquals(WishClaimState.AMBIGUOUS, result.state());
    }

    private WishTreeService service(double random) {
        YamlConfiguration yaml;
        try (InputStreamReader reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("resources/wish_tree_rewards.yml"),
                StandardCharsets.UTF_8)) {
            yaml = YamlConfiguration.loadConfiguration(reader);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
        WishTreeService service = new WishTreeService(plugin, yaml, () -> random);
        service.setVisualService(new NoopWishTreeVisualService(service.getStore()));
        return service;
    }

    private Economy economy() {
        return (Economy) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Economy.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "WishTestEconomy";
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
}
