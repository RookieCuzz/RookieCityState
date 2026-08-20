package com.cuzz.rookiecitystate.social;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.api.event.CityLikedEvent;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.world.CityLifecycleState;
import com.cuzz.rookiecitystate.world.CityWorldState;
import com.cuzz.rookiecitystate.world.WorldVisibility;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.File;
import java.lang.reflect.Proxy;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class CitySocialServiceTest {
    private ServerMock server;
    private RookieCityState plugin;
    private PlayerMock visitor;

    @BeforeEach void setUp() {
        server = MockBukkit.mock();
        PluginMock vault = MockBukkit.createMockPlugin("Vault");
        for (String dependency : List.of("BKCommonLib", "My_Worlds", "FastAsyncWorldEdit", "WorldGuard", "RookieRegions", "ModelEngine")) {
            MockBukkit.createMockPlugin(dependency);
        }
        server.getServicesManager().register(Economy.class, economy(), vault, org.bukkit.plugin.ServicePriority.Normal);
        plugin = MockBukkit.load(RookieCityState.class);
        if (server.getWorld("world") == null) server.addSimpleWorld("world");
        visitor = server.addPlayer("Visitor");
    }

    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test void qualificationLikeAndHotScoreFormAClosedFlow() {
        CityState city = publicCity("Owner", "PopularOne");
        enter(visitor, city);
        assertTrue(plugin.getCitySocialService().qualify(visitor, city));
        CityLikeResult result = plugin.getCitySocialService().like(visitor, city).toCompletableFuture().join();
        assertTrue(result.success(), result.message());
        assertEquals(4, result.remainingVotes());

        CitySocialView view = plugin.getCitySocialService().getView(visitor, city);
        assertEquals(1L, view.totalLikes());
        assertEquals(1, view.recentVisitors());
        assertEquals(1, view.recentLikes());
        assertEquals(4L, view.hotScore());
        assertEquals(1, view.hotRank());
    }

    @Test void ownCityDuplicateAndGlobalSixthVoteAreRejected() {
        CityState own = publicCity("OwnOwner", "OwnCity");
        PlayerMock owner = (PlayerMock) own.getOwner().getCityStatePlayer().getBukkitPlayer();
        assertEquals(CityLikeResult.Status.OWN_CITY,
                plugin.getCitySocialService().like(owner, own).toCompletableFuture().join().status());

        CityLikeResult first = null;
        for (int i = 0; i < 6; i++) {
            CityState city = publicCity("Owner" + i, "Target" + i);
            enter(visitor, city);
            assertTrue(plugin.getCitySocialService().qualify(visitor, city));
            CityLikeResult result = plugin.getCitySocialService().like(visitor, city).toCompletableFuture().join();
            if (i == 0) first = result;
            if (i < 5) assertTrue(result.success(), result.message());
            else assertEquals(CityLikeResult.Status.WEEKLY_LIMIT, result.status());
        }
        assertNotNull(first);
        CityState firstCity = plugin.getCityStateManager().getCityStateByName("Target0");
        assertEquals(CityLikeResult.Status.ALREADY_LIKED,
                plugin.getCitySocialService().like(visitor, firstCity).toCompletableFuture().join().status());
    }

    @Test void privateCityLeavesPopularListButKeepsMetrics() {
        CityState city = publicCity("OwnerPrivate", "WillBePrivate");
        enter(visitor, city);
        plugin.getCitySocialService().qualify(visitor, city);
        plugin.getCitySocialService().like(visitor, city).toCompletableFuture().join();
        assertFalse(plugin.getCitySocialService().getPopularCities().isEmpty());

        city.setWorldVisibility(WorldVisibility.PRIVATE);
        plugin.getCitySocialService().onVisibilityChanged(city);
        assertTrue(plugin.getCitySocialService().getPopularCities().stream()
                .noneMatch(entry -> entry.cityState() == city));
        assertEquals(1L, plugin.getCitySocialService().getView(visitor, city).totalLikes());

        city.setWorldVisibility(WorldVisibility.PUBLIC);
        plugin.getCitySocialService().onVisibilityChanged(city);
        assertTrue(plugin.getCitySocialService().getPopularCities().stream()
                .anyMatch(entry -> entry.cityState() == city));
    }

    @Test void trackerRequiresContinuousSixtySeconds() {
        CityState city = publicCity("OwnerTimer", "TimedCity");
        AtomicLong time = new AtomicLong(ZonedDateTime.of(2026, 8, 17, 10, 0, 0, 0,
                ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());
        CitySocialConfig config = CitySocialConfig.load(YamlFiles.load(
                new File("src/main/resources/resources/city_social.yml")));
        CitySocialService service = new CitySocialService(plugin, config, time::get);
        service.load();
        CityVisitTracker tracker = new CityVisitTracker(plugin, service, time::get, false);

        enter(visitor, city);
        tracker.tick();
        assertEquals(1, tracker.sessionCount());
        tracker.onWorldChanged(new PlayerChangedWorldEvent(visitor, server.getWorld("world")));
        assertEquals(0, tracker.sessionCount());
        time.addAndGet(59_000L); tracker.tick();
        assertFalse(service.isQualified(visitor, city));
        visitor.teleport(new Location(server.getWorld("world"), 0, 64, 0));
        tracker.tick();
        enter(visitor, city); tracker.tick();
        time.addAndGet(60_000L); tracker.tick();
        assertTrue(service.isQualified(visitor, city));
        tracker.shutdown();
    }

    @Test void joiningTheVisitedCityImmediatelyCancelsTheSession() {
        CityState city = publicCity("OwnerJoin", "JoinCancelsVisit");
        CityVisitTracker tracker = new CityVisitTracker(plugin, plugin.getCitySocialService(),
                System::currentTimeMillis, false);
        plugin.getCitySocialService().setTracker(tracker);
        enter(visitor, city);
        tracker.tick();
        assertEquals(1, tracker.sessionCount());

        city.addMember(plugin.getCityStatePlayerManager().getCityStatePlayer(visitor));
        assertEquals(0, tracker.sessionCount());
        tracker.shutdown();
    }

    @Test void failedLikeSaveDoesNotConsumeVoteUpdateRankingOrFireEvent() throws Exception {
        CityState city = publicCity("OwnerFailure", "SaveFailureCity");
        enter(visitor, city);
        assertTrue(plugin.getCitySocialService().qualify(visitor, city));
        AtomicInteger events = new AtomicInteger();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler public void onLike(CityLikedEvent event) { events.incrementAndGet(); }
        }, plugin);
        File file = new File(plugin.getDataFolder(), "data/social/cities/" + city.getUuid() + ".yml");
        assertTrue(file.delete());
        assertTrue(file.mkdir());

        CityLikeResult result = plugin.getCitySocialService().like(visitor, city).toCompletableFuture().join();
        assertEquals(CityLikeResult.Status.SAVE_FAILED, result.status());
        CitySocialView view = plugin.getCitySocialService().getView(visitor, city);
        assertEquals(0L, view.totalLikes());
        assertEquals(5, view.votesRemaining());
        assertEquals(1L, view.hotScore());
        assertEquals(0, events.get());
    }

    @Test void zeroHeatCitiesUseUuidAsStableFinalTieBreaker() {
        CityState first = publicCity("OwnerZeroA", "ZeroA");
        CityState second = publicCity("OwnerZeroB", "ZeroB");
        List<CityPopularityEntry> entries = plugin.getCitySocialService().getPopularCities();
        assertEquals(2, entries.size());
        List<String> actual = entries.stream().map(entry -> entry.cityState().getUuid().toString()).toList();
        List<String> expected = List.of(first.getUuid().toString(), second.getUuid().toString()).stream().sorted().toList();
        assertEquals(expected, actual);
        assertTrue(entries.stream().allMatch(entry -> entry.hotScore() == 0L));
    }

    @Test void qualificationExpiresAtWeekBoundaryAndLoyalVisitorCanLikeAgain() {
        CityState city = publicCity("OwnerWeek", "WeeklyCity");
        AtomicLong time = new AtomicLong(ZonedDateTime.of(2026, 8, 17, 10, 0, 0, 0,
                ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());
        CitySocialConfig config = CitySocialConfig.load(YamlFiles.load(
                new File("src/main/resources/resources/city_social.yml")));
        CitySocialService service = new CitySocialService(plugin, config, time::get);
        service.load();
        enter(visitor, city);
        assertTrue(service.qualify(visitor, city));
        assertTrue(service.like(visitor, city).toCompletableFuture().join().success());

        time.addAndGet(7L * 24L * 60L * 60L * 1000L);
        assertFalse(service.isQualified(visitor, city));
        assertTrue(service.qualify(visitor, city));
        assertTrue(service.like(visitor, city).toCompletableFuture().join().success());
        assertEquals(2L, service.getView(visitor, city).totalLikes());
    }

    @Test void archivingLikedCityDoesNotRefundWeeklyVoteQuota() {
        CityState city = publicCity("OwnerArchiveVote", "ArchiveVoteCity");
        CitySocialService service = plugin.getCitySocialService();
        enter(visitor, city);
        assertTrue(service.qualify(visitor, city));
        assertTrue(service.like(visitor, city).toCompletableFuture().join().success());
        assertEquals(1, service.getPlayerStatus(visitor.getUniqueId()).votesUsed());

        service.archive(city);

        assertEquals(1, service.getPlayerStatus(visitor.getUniqueId()).votesUsed());
    }

    private CityState publicCity(String ownerName, String cityName) {
        PlayerMock owner = server.addPlayer(ownerName);
        CityStatePlayer data = plugin.getCityStatePlayerManager().getCityStatePlayer(owner);
        plugin.getCityStateManager().createCityState(data, cityName);
        CityState city = data.getCityState();
        city.transitionWorld(CityLifecycleState.ACTIVE, CityWorldState.READY, null);
        city.setWorldVisibility(WorldVisibility.PUBLIC);
        if (server.getWorld(city.getWorldName()) == null) server.addSimpleWorld(city.getWorldName());
        plugin.getCitySocialService().refresh();
        return city;
    }

    private void enter(PlayerMock player, CityState city) {
        player.teleport(new Location(server.getWorld(city.getWorldName()), 0, 65, 0));
    }

    private Economy economy() {
        return (Economy) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Economy.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "SocialTestEconomy";
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
