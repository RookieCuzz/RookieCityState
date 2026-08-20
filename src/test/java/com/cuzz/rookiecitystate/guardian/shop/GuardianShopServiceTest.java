package com.cuzz.rookiecitystate.guardian.shop;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.world.CityLifecycleState;
import com.cuzz.rookiecitystate.world.CityWorldState;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bukkit.configuration.file.YamlConfiguration;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class GuardianShopServiceTest {
    private ServerMock server;
    private RookieCityState plugin;
    private PlayerMock player;
    private CityStatePlayer data;
    private CityState city;
    private GuardianContributionShopService service;

    @BeforeEach void setUp() {
        server = MockBukkit.mock();
        PluginMock vault = MockBukkit.createMockPlugin("Vault");
        for (String dependency : List.of("BKCommonLib", "My_Worlds", "FastAsyncWorldEdit", "WorldGuard", "RookieRegions", "ModelEngine")) {
            MockBukkit.createMockPlugin(dependency);
        }
        server.getServicesManager().register(Economy.class, economy(), vault, org.bukkit.plugin.ServicePriority.Normal);
        plugin = MockBukkit.load(RookieCityState.class);
        player = server.addPlayer("ShopOwner");
        data = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        plugin.getCityStateManager().createCityState(data, "ShopTest");
        city = data.getCityState();
        city.transitionWorld(CityLifecycleState.ACTIVE, CityWorldState.READY, null);
        service = plugin.getGuardianContributionShopService();
    }

    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test void purchaseSpendsOnlyAvailableAndCreatesOwnedOrInboxRecord() {
        plugin.getGuardianBeastService().grantContribution(data, 5_000L);
        GuardianShopProduct product = service.getCurrentRotation().products().stream()
                .filter(item -> item.minimumGuardianLevel() == 0).findFirst().orElseThrow();
        GuardianPurchaseResult result = service.purchase(player, city, product.id()).toCompletableFuture().join();
        assertTrue(result.success(), result.message());
        long[] contribution = plugin.getGuardianBeastService().playerContribution(data);
        assertEquals(5_000L - product.price(), contribution[0]);
        assertEquals(5_000L, contribution[1]);
        if (product.permanent()) assertTrue(service.owned(data).containsKey(product.id()));
        else assertEquals(1, plugin.getWishTreeService().mailbox(data).size());
    }

    @Test void permanentProductCannotBeBoughtTwice() {
        plugin.getGuardianBeastService().grantContribution(data, 5_000L);
        GuardianShopProduct product = service.getCurrentRotation().products().stream()
                .filter(GuardianShopProduct::permanent).filter(item -> item.minimumGuardianLevel() == 0)
                .findFirst().orElseThrow();
        assertTrue(service.purchase(player, city, product.id()).toCompletableFuture().join().success());
        long available = plugin.getGuardianBeastService().playerContribution(data)[0];
        GuardianPurchaseResult second = service.purchase(player, city, product.id()).toCompletableFuture().join();
        assertEquals(GuardianPurchaseResult.Status.ALREADY_OWNED, second.status());
        assertEquals(available, plugin.getGuardianBeastService().playerContribution(data)[0]);
    }

    @Test void saveFailureRollsBackContributionAndReward() throws Exception {
        plugin.getGuardianBeastService().grantContribution(data, 5_000L);
        GuardianShopProduct product = service.getCurrentRotation().products().stream()
                .filter(item -> item.minimumGuardianLevel() == 0).findFirst().orElseThrow();
        Files.delete(data.getDataFile().toPath());
        Files.createDirectory(data.getDataFile().toPath());
        GuardianPurchaseResult result = service.purchase(player, city, product.id()).toCompletableFuture().join();
        assertEquals(GuardianPurchaseResult.Status.SAVE_FAILED, result.status());
        assertEquals(5_000L, data.getYaml().getLong("guardian_beast.contribution.available"));
        assertFalse(service.owned(data).containsKey(product.id()));
    }

    @Test void equippedIdentityPausesWhenCityIsUnavailableAndReturnsAfterRecovery() {
        service.grantProduct(data, "title_friend");
        service.equip(player, GuardianCosmeticSlot.TITLE, "title_friend");
        assertTrue(service.activeText(data, GuardianCosmeticSlot.TITLE).contains("灵兽之友"));
        city.transitionWorld(CityLifecycleState.ERROR, CityWorldState.ERROR, "test");
        assertEquals("", service.activeText(data, GuardianCosmeticSlot.TITLE));
        city.transitionWorld(CityLifecycleState.ACTIVE, CityWorldState.READY, null);
        assertTrue(service.activeText(data, GuardianCosmeticSlot.TITLE).contains("灵兽之友"));
    }

    @Test void consumableHonorsWeeklyLimitAndUsesSharedInbox() {
        YamlConfiguration shopYaml = YamlFiles.load(new File(plugin.getDataFolder(), "config/guardian_shop.yml"));
        shopYaml.set("rotation.size", 21);
        service.reloadConfig(GuardianShopConfig.load(shopYaml));
        service.rotateNow();
        plugin.getGuardianBeastService().grantContribution(data, 5_000L);

        GuardianPurchaseResult first = service.purchase(player, city, "stone_three").toCompletableFuture().join();
        assertTrue(first.success(), first.message());
        assertEquals(1, plugin.getWishTreeService().mailbox(data).size());
        long available = plugin.getGuardianBeastService().playerContribution(data)[0];

        GuardianPurchaseResult second = service.purchase(player, city, "stone_three").toCompletableFuture().join();
        assertEquals(GuardianPurchaseResult.Status.WEEKLY_LIMIT, second.status());
        assertEquals(available, plugin.getGuardianBeastService().playerContribution(data)[0]);
        assertEquals(5_000L, plugin.getGuardianBeastService().playerContribution(data)[1]);
        assertEquals(1, plugin.getWishTreeService().mailbox(data).size());
    }

    @Test void revokingPermanentProductAlsoUnequipsIt() {
        service.grantProduct(data, "prefix_feeder");
        service.equip(player, GuardianCosmeticSlot.CHAT_PREFIX, "prefix_feeder");
        assertFalse(service.activeText(data, GuardianCosmeticSlot.CHAT_PREFIX).isEmpty());

        service.revokeProduct(data, "prefix_feeder");
        assertFalse(service.owned(data).containsKey("prefix_feeder"));
        assertEquals("", service.activeText(data, GuardianCosmeticSlot.CHAT_PREFIX));
    }

    private Economy economy() {
        return (Economy) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Economy.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "ShopTestEconomy";
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
