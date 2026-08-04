package com.cuzz.rookiecitystate;

import com.cuzz.rookiecitystate.api.event.CityStateCreatedEvent;
import com.cuzz.rookiecitystate.api.event.CityStateDeletedEvent;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.CityStateManager;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.citystate.member.CityStatePermission;
import com.cuzz.rookiecitystate.gui.entities.CityStateJoinCheckGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.internal.chat.ChatInterceptor;
import com.cuzz.rookiecitystate.internal.inventory.InventoryBuilder;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import com.cuzz.rookiecitystate.listener.TpAllPressTracker;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.request.Request;
import com.cuzz.rookiecitystate.request.entities.TpAllRequest;
import com.cuzz.rookiecitystate.transaction.TransactionService;
import com.cuzz.rookiecitystate.thirdparty.PlaceholderAPIExpansion;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.ArrowMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.InputStreamReader;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RookieCityStateIntegrationTest {
    private ServerMock server;
    private RookieCityState plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PluginMock vault = MockBukkit.createMockPlugin("Vault");
        server.getServicesManager().register(Economy.class, economy(), vault, org.bukkit.plugin.ServicePriority.Normal);
        plugin = MockBukkit.load(RookieCityState.class);
        if (server.getWorld("world") == null) server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void loadsWithoutOptionalPluginsAndDeclaresExpectedVersion() {
        assertTrue(plugin.isEnabled(), plugin.getDataFolder().getAbsolutePath());
        assertEquals("RookieCityState", plugin.getPluginMeta().getName());
        assertEquals("1.0.0", plugin.getPluginMeta().getVersion());
        assertFalse(server.getPluginManager().isPluginEnabled("PlaceholderAPI"));
        assertFalse(server.getPluginManager().isPluginEnabled("PlayerPoints"));
    }

    @Test
    void allBundledMaterialsAreValidAndShopChineseIsUtf8() throws Exception {
        for (String resource : List.of(
                "resources/shop/Shop1.yml", "resources/shop/Shop2.yml",
                "resources/gui/MainGUI.yml", "resources/gui/CityStateInfoGUI.yml",
                "resources/gui/CityStateJoinCheckGUI.yml", "resources/gui/CityStateMemberManageGUI.yml",
                "resources/gui/CityStateMemberListGUI.yml", "resources/gui/CityStateMineGUI.yml")) {
            YamlConfiguration yaml;
            try (InputStreamReader reader = new InputStreamReader(
                    getClass().getClassLoader().getResourceAsStream(resource), StandardCharsets.UTF_8)) {
                yaml = YamlConfiguration.loadConfiguration(reader);
            }
            assertMaterials(yaml);
            if (resource.contains("Shop")) assertTrue(yaml.saveToString().contains("城邦"));
        }
    }

    @Test
    void invalidReloadRetainsPreviousSettingsAndBadIconBecomesBarrier() {
        int previousCapacity = MainSettings.getCityStateDefaultMaxMemberCount();
        File settingsFile = new File(plugin.getDataFolder(), "config/settings.yml");
        YamlConfiguration settings = YamlFiles.load(settingsFile);
        settings.set("city_state.default_max_member_count", 0);
        YamlFiles.save(settings, settingsFile);
        assertFalse(plugin.reloadPlugin());
        assertEquals(previousCapacity, MainSettings.getCityStateDefaultMaxMemberCount());

        YamlConfiguration icon = new YamlConfiguration();
        icon.set("material", "NOT_A_MATERIAL");
        assertEquals(Material.BARRIER, GUIItemManager.getItemBuilder(icon, null).build().getType());

        settings.set("city_state.default_max_member_count", previousCapacity);
        YamlFiles.save(settings, settingsFile);
        assertTrue(plugin.reloadPlugin());
    }

    @Test
    void guiCancelsTakingItems() {
        PlayerMock player = server.addPlayer();
        Inventory inventory = new InventoryBuilder().title("test").row(1)
                .item(0, new org.bukkit.inventory.ItemStack(Material.DIAMOND)).build();
        player.openInventory(inventory);
        InventoryClickEvent event = player.simulateInventoryClick(0);
        assertTrue(event.isCancelled());
        assertNull(player.getInventory().getItem(0));

    }

    @Test
    void onlyNewCommandsAndAdminPermissionAreAvailable() {
        PlayerMock player = server.addPlayer();
        assertFalse(player.hasPermission("rookiecitystate.admin"));
        assertTrue(player.performCommand("cs plugin reload"));
        String denied = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(player.nextComponentMessage());
        assertTrue(denied.contains("无权限"));

        assertTrue(player.performCommand("cs plugin version"));
        String version = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(player.nextComponentMessage());
        assertTrue(version.contains("1.0.0"));

        String removedName = "gu" + "ild";
        assertNull(plugin.getCommand("j" + removedName));
        assertNull(plugin.getCommand(removedName));
        assertNull(plugin.getCommand("jg"));
        assertEquals("rookiecitystate", new PlaceholderAPIExpansion().getIdentifier());
    }

    @Test
    void sourceAndResourcesContainNoRemovedBrand() throws Exception {
        String removedName = "gu" + "ild";
        String removedBrand = "july" + removedName;
        try (var paths = Files.walk(Path.of("src"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relative = Path.of("src").relativize(path).toString().toLowerCase(java.util.Locale.ROOT);
                String content = Files.readString(path, StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
                assertFalse(relative.contains(removedName), relative);
                assertFalse(content.contains(removedBrand), relative);
                assertFalse(content.contains(removedName), relative);
            }
        }
        try (var paths = Files.walk(Path.of("target", "classes"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relative = Path.of("target", "classes").relativize(path)
                        .toString().toLowerCase(java.util.Locale.ROOT);
                String content = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1)
                        .toLowerCase(java.util.Locale.ROOT);
                assertFalse(relative.contains(removedName), relative);
                assertFalse(content.contains(removedBrand), relative);
                assertFalse(content.contains(removedName), relative);
            }
        }
    }

    @Test
    void duplicateNamesAndFullCityStateAreRejectedAtomically() {
        PlayerMock owner = server.addPlayer("Owner");
        CityStatePlayer ownerData = plugin.getCityStatePlayerManager().getCityStatePlayer(owner);
        plugin.getCityStateManager().createCityState(ownerData, "§aAlpha");
        PlayerMock duplicateOwner = server.addPlayer("OtherOwner");
        assertThrows(IllegalArgumentException.class, () -> plugin.getCityStateManager().createCityState(
                plugin.getCityStatePlayerManager().getCityStatePlayer(duplicateOwner), "alpha"));

        CityState cityState = ownerData.getCityState();
        while (cityState.getMemberCount() < cityState.getMaxMemberCount()) {
            PlayerMock member = server.addPlayer("Member" + cityState.getMemberCount());
            cityState.addMember(plugin.getCityStatePlayerManager().getCityStatePlayer(member));
        }
        PlayerMock overflow = server.addPlayer("Overflow");
        assertThrows(IllegalStateException.class,
                () -> cityState.addMember(plugin.getCityStatePlayerManager().getCityStatePlayer(overflow)));
    }

    @Test
    void joinApprovalRequiresExplicitPermission() {
        PlayerMock owner = server.addPlayer("Owner");
        PlayerMock memberPlayer = server.addPlayer("Approver");
        CityStatePlayer ownerData = plugin.getCityStatePlayerManager().getCityStatePlayer(owner);
        plugin.getCityStateManager().createCityState(ownerData, "ApprovalTest");
        CityState cityState = ownerData.getCityState();
        CityStatePlayer memberData = plugin.getCityStatePlayerManager().getCityStatePlayer(memberPlayer);
        cityState.addMember(memberData);
        CityStateMember member = cityState.getMember(memberData);

        assertFalse(new CityStateJoinCheckGUI(null, member).canUse());
        member.setPermission(CityStatePermission.PLAYER_JOIN_CHECK, true);
        assertTrue(new CityStateJoinCheckGUI(null, member).canUse());
    }

    @Test
    void meleeAndProjectileFriendlyFireAreCancelled() {
        PlayerMock owner = server.addPlayer("Owner");
        PlayerMock memberPlayer = server.addPlayer("Member");
        CityStatePlayer ownerData = plugin.getCityStatePlayerManager().getCityStatePlayer(owner);
        plugin.getCityStateManager().createCityState(ownerData, "PvPTest");
        CityState cityState = ownerData.getCityState();
        cityState.addMember(plugin.getCityStatePlayerManager().getCityStatePlayer(memberPlayer));
        cityState.setMemberDamageEnabled(false);

        EntityDamageByEntityEvent melee = new EntityDamageByEntityEvent(owner, memberPlayer,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, 2D);
        server.getPluginManager().callEvent(melee);
        assertTrue(melee.isCancelled());

        ArrowMock arrow = new ArrowMock(server, UUID.randomUUID());
        arrow.setShooter(owner);
        EntityDamageByEntityEvent projectile = new EntityDamageByEntityEvent(arrow, memberPlayer,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE, 2D);
        server.getPluginManager().callEvent(projectile);
        assertTrue(projectile.isCancelled());
    }

    @Test
    void failedRewardRefundsAndFreePaymentIsAccepted() {
        AtomicInteger charged = new AtomicInteger();
        AtomicInteger refunded = new AtomicInteger();
        TransactionService.Payment payment = new TransactionService.Payment() {
            public void validate() { }
            public boolean charge() { charged.incrementAndGet(); return true; }
            public boolean refund() { refunded.incrementAndGet(); return true; }
        };
        TransactionService.Result failed = plugin.getTransactionService().execute("test", () -> { }, payment,
                () -> { throw new IllegalStateException("reward failed"); });
        assertFalse(failed.success());
        assertEquals(1, charged.get());
        assertEquals(1, refunded.get());

        TransactionService.Result free = plugin.getTransactionService().execute("free", () -> { },
                plugin.getTransactionService().vault(plugin.getVaultEconomy(), server.addPlayer(), 0D), () -> { });
        assertTrue(free.success());
    }

    @Test
    void placeholderSnapshotUsesDonatedAmount() {
        PlayerMock owner = server.addPlayer("Owner");
        CityStatePlayer ownerData = plugin.getCityStatePlayerManager().getCityStatePlayer(owner);
        plugin.getCityStateManager().createCityState(ownerData, "PlaceholderTest");
        CityStateMember member = ownerData.getCityState().getMember(ownerData);
        member.setDonated(com.cuzz.rookiecitystate.citystate.CityStateBank.BalanceType.GMONEY,
                new BigDecimal("123.45"));

        plugin.getPlaceholderSnapshotService().refresh();
        assertEquals("123.45", plugin.getPlaceholderSnapshotService()
                .get(owner.getUniqueId(), "member_donated_gmoney"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatInputKeepsOnlyTheNewestSession() throws Exception {
        PlayerMock player = server.addPlayer();
        ChatInterceptor first = new ChatInterceptor.Builder().plugin(plugin).player(player)
                .onlyFirst(true).chatListener(message -> { }).build();
        ChatInterceptor second = new ChatInterceptor.Builder().plugin(plugin).player(player)
                .onlyFirst(true).chatListener(message -> { }).build();
        first.register();
        second.register();

        Field activeField = ChatInterceptor.class.getDeclaredField("ACTIVE");
        activeField.setAccessible(true);
        Map<UUID, ChatInterceptor> active = (Map<UUID, ChatInterceptor>) activeField.get(null);
        assertSame(second, active.get(player.getUniqueId()));
        second.unregister();
    }

    @Test
    void teleportReplacementAndStrictShiftCountingAreDeterministic() {
        PlayerMock player = server.addPlayer();
        org.bukkit.Location destination = new org.bukkit.Location(server.getWorld("world"), 20, 70, 20);
        assertTrue(plugin.getTeleportService().begin(player, destination, 10, ignored -> { },
                () -> { }, () -> { }, failure -> fail(failure.getMessage())));
        assertEquals(1, plugin.getTeleportService().getPendingCount());
        assertTrue(plugin.getTeleportService().begin(player, destination, 10, ignored -> { },
                () -> { }, () -> { }, failure -> fail(failure.getMessage())));
        assertEquals(1, plugin.getTeleportService().getPendingCount());
        plugin.getTeleportService().handleMove(player, player.getLocation(), player.getLocation().add(1, 0, 0));
        assertEquals(0, plugin.getTeleportService().getPendingCount());

        TpAllPressTracker tracker = new TpAllPressTracker();
        UUID request = UUID.randomUUID();
        assertEquals(1, tracker.press(player.getUniqueId(), request, 1_000, 500));
        assertEquals(2, tracker.press(player.getUniqueId(), request, 1_500, 500));
        assertEquals(3, tracker.press(player.getUniqueId(), request, 2_000, 500));
        assertEquals(1, tracker.press(player.getUniqueId(), request, 2_501, 500));
    }

    @Test
    void tpAllPersistsOriginalLocationAndReloadsWithStableKeys() {
        PlayerMock owner = server.addPlayer("Owner");
        PlayerMock memberPlayer = server.addPlayer("Member");
        CityStatePlayer ownerData = plugin.getCityStatePlayerManager().getCityStatePlayer(owner);
        plugin.getCityStateManager().createCityState(ownerData, "Teleporters");
        CityState cityState = ownerData.getCityState();
        CityStatePlayer memberData = plugin.getCityStatePlayerManager().getCityStatePlayer(memberPlayer);
        cityState.addMember(memberData);
        CityStateMember sender = cityState.getMember(ownerData);
        CityStateMember receiver = cityState.getMember(memberData);
        org.bukkit.Location location = new org.bukkit.Location(server.getWorld("world"), 12.5, 70, -4.25, 90, 5);
        TpAllRequest request = new TpAllRequest(sender, receiver, location);
        request.send();
        location.setX(999);

        plugin.getRequestManager().loadRequests();
        Request loaded = receiver.getReceivedRequests().stream()
                .filter(value -> value.getType() == Request.Type.TP_ALL).findFirst().orElseThrow();
        assertInstanceOf(TpAllRequest.class, loaded);
        assertEquals(12.5, ((TpAllRequest) loaded).getLocation().getX());
    }

    @Test
    void customEventsExposeStaticHandlerLists() {
        assertSame(CityStateCreatedEvent.getHandlerList(), new CityStateCreatedEvent(nullCityState(), nullPlayer()).getHandlers());
        assertSame(CityStateDeletedEvent.getHandlerList(), new CityStateDeletedEvent(nullCityState()).getHandlers());
    }

    private CityState nullCityState() {
        PlayerMock owner = server.addPlayer("EventOwner" + UUID.randomUUID().toString().substring(0, 4));
        CityStatePlayer data = plugin.getCityStatePlayerManager().getCityStatePlayer(owner);
        plugin.getCityStateManager().createCityState(data, "EventCityState" + UUID.randomUUID());
        return data.getCityState();
    }

    private CityStatePlayer nullPlayer() {
        return plugin.getCityStatePlayerManager().getCityStatePlayer(server.addPlayer());
    }

    private void assertMaterials(ConfigurationSection section) {
        for (String key : section.getKeys(true)) {
            if (!key.endsWith("material")) continue;
            String value = section.getString(key);
            assertNotNull(Material.matchMaterial(value), () -> "invalid material at " + key + ": " + value);
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
        if (type == float.class) return 0F;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return null;
    }
}
