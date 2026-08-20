package com.cuzz.rookiecitystate;

import com.cuzz.rookiecitystate.command.CityStateCommand;
import com.cuzz.rookiecitystate.config.Shop;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.citystate.CacheCityStateManager;
import com.cuzz.rookiecitystate.citystate.CityStateManager;
import com.cuzz.rookiecitystate.internal.chat.ChatInterceptor;
import com.cuzz.rookiecitystate.internal.config.SettingsLoader;
import com.cuzz.rookiecitystate.internal.io.ResourceFiles;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import com.cuzz.rookiecitystate.internal.assets.BundledResourceCatalog;
import com.cuzz.rookiecitystate.listener.EssentialsChatListener;
import com.cuzz.rookiecitystate.listener.GUIListener;
import com.cuzz.rookiecitystate.listener.MemberDamageListener;
import com.cuzz.rookiecitystate.listener.PlayerListener;
import com.cuzz.rookiecitystate.listener.TeleportListener;
import com.cuzz.rookiecitystate.listener.TpAllListener;
import com.cuzz.rookiecitystate.listener.CityWorldAccessListener;
import com.cuzz.rookiecitystate.listener.CityProtectionSyncLockListener;
import com.cuzz.rookiecitystate.listener.CityWorldProtectionListener;
import com.cuzz.rookiecitystate.listener.WishTreeInteractionListener;
import com.cuzz.rookiecitystate.listener.GuardianInteractionListener;
import com.cuzz.rookiecitystate.guardian.GuardianBeastConfig;
import com.cuzz.rookiecitystate.guardian.GuardianBeastService;
import com.cuzz.rookiecitystate.guardian.GuardianBlueprintInstaller;
import com.cuzz.rookiecitystate.guardian.GuardianVisualService;
import com.cuzz.rookiecitystate.guardian.ModelEngineGuardianVisualService;
import com.cuzz.rookiecitystate.guardian.ModelEngineGuardianBackend;
import com.cuzz.rookiecitystate.guardian.shop.GuardianContributionShopService;
import com.cuzz.rookiecitystate.guardian.shop.GuardianShopConfig;
import com.cuzz.rookiecitystate.social.CitySocialConfig;
import com.cuzz.rookiecitystate.social.CitySocialService;
import com.cuzz.rookiecitystate.social.CityVisitTracker;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.player.CityStatePlayerManager;
import com.cuzz.rookiecitystate.placeholder.PlaceholderSnapshotService;
import com.cuzz.rookiecitystate.request.RequestManager;
import com.cuzz.rookiecitystate.task.LoggerSaveTask;
import com.cuzz.rookiecitystate.task.RequestCleanTask;
import com.cuzz.rookiecitystate.thirdparty.PlaceholderAPIExpansion;
import com.cuzz.rookiecitystate.thirdparty.IntegrationHandle;
import com.cuzz.rookiecitystate.thirdparty.economy.PlayerPointsEconomy;
import com.cuzz.rookiecitystate.thirdparty.economy.VaultEconomy;
import com.cuzz.rookiecitystate.transaction.TransactionService;
import com.cuzz.rookiecitystate.teleport.TeleportService;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.world.CityStateLifecycleService;
import com.cuzz.rookiecitystate.world.CityWorldBackend;
import com.cuzz.rookiecitystate.world.CityWorldProtectionService;
import com.cuzz.rookiecitystate.world.CityWorldService;
import com.cuzz.rookiecitystate.world.NoopCityWorldProtectionService;
import com.cuzz.rookiecitystate.world.UnavailableCityWorldBackend;
import com.cuzz.rookiecitystate.world.myworlds.MyWorldsCityWorldBackend;
import com.cuzz.rookiecitystate.world.operation.WorldOperationStore;
import com.cuzz.rookiecitystate.world.rookieregions.RookieRegionsCityWorldProtectionService;
import com.cuzz.rookiecitystate.wishtree.NoopWishTreeVisualService;
import com.cuzz.rookiecitystate.wishtree.WishRewardCatalog;
import com.cuzz.rookiecitystate.wishtree.WishTreeService;
import com.cuzz.rookiecitystate.wishtree.WishTreeVisualService;
import com.cuzz.rookiecitystate.wishtree.WorldEditWishTreeVisualService;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RookieCityState extends JavaPlugin {

    private static RookieCityState instance;

    private IntegrationHandle placeholderAPIExpansion;
    private CityStateManager cityStateManager;
    private CityStatePlayerManager cityStatePlayerManager;
    private CacheCityStateManager cacheCityStateManager;
    private RequestManager requestManager;
    private VaultEconomy vaultEconomy;
    private PlayerPointsEconomy playerPointsEconomy;
    private PluginManager pluginManager;
    private final TransactionService transactionService = new TransactionService();
    private TeleportService teleportService;
    private TpAllListener tpAllListener;
    private PlaceholderSnapshotService placeholderSnapshotService;
    private CityWorldService cityWorldService;
    private CityStateLifecycleService cityStateLifecycleService;
    private WorldOperationStore worldOperationStore;
    private WishTreeService wishTreeService;
    private WishTreeVisualService wishTreeVisualService;
    private GuardianBeastService guardianBeastService;
    private GuardianVisualService guardianVisualService;
    private GuardianBlueprintInstaller guardianBlueprintInstaller;
    private ModelEngineGuardianBackend guardianModelBackend;
    private GuardianContributionShopService guardianContributionShopService;
    private CitySocialService citySocialService;
    private CityVisitTracker cityVisitTracker;

    private YamlConfiguration langYaml;
    private YamlConfiguration wishTreeRewardsYaml;
    private GuardianBeastConfig guardianBeastConfig;
    private GuardianShopConfig guardianShopConfig;
    private CitySocialConfig citySocialConfig;
    private Map<String, YamlConfiguration> guiYamlMap = Map.of();
    private Map<String, Shop> shopYamlMap = Map.of();

    @Override
    public void onEnable() {
        instance = this;
        PluginLogger.init();
        PluginLogger.info("RookieCityState v" + getPluginMeta().getVersion() + " 正在启动。");

        pluginManager = getServer().getPluginManager();
        cityStatePlayerManager = new CityStatePlayerManager();
        cityStateManager = new CityStateManager();
        cacheCityStateManager = new CacheCityStateManager();
        requestManager = new RequestManager();
        teleportService = new TeleportService(this);
        placeholderSnapshotService = new PlaceholderSnapshotService(this);

        try {
            YamlFiles.recoverAtomicWrites(getDataFolder().toPath());
            loadConfigSnapshot();
        } catch (RuntimeException exception) {
            PluginLogger.error("配置加载失败，插件将停用。", exception);
            getLogger().log(java.util.logging.Level.SEVERE, "配置加载失败，插件将停用。", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!hookVault()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        hookOptionalPlugins();

        try {
            worldOperationStore = new WorldOperationStore(this);
            CityWorldBackend worldBackend;
            CityWorldProtectionService protectionService;
            try {
                worldBackend = new MyWorldsCityWorldBackend();
            } catch (Throwable exception) {
                if (isMockEnvironment()) {
                    worldBackend = new UnavailableCityWorldBackend("MockBukkit 不提供 MyWorlds API");
                } else {
                    throw new IllegalStateException("MyWorlds API 初始化失败", exception);
                }
            }
            if (isMockEnvironment()) {
                protectionService = new NoopCityWorldProtectionService();
            } else {
                protectionService = RookieRegionsCityWorldProtectionService.fromServices(this);
            }
            cityWorldService = new CityWorldService(this, worldBackend, protectionService, worldOperationStore);
            cityStateLifecycleService = new CityStateLifecycleService(this, cityWorldService, worldOperationStore);
            wishTreeService = new WishTreeService(this, wishTreeRewardsYaml);
            wishTreeVisualService = isMockEnvironment()
                    ? new NoopWishTreeVisualService(wishTreeService.getStore())
                    : new WorldEditWishTreeVisualService(this, cityWorldService, protectionService, wishTreeService.getStore());
            wishTreeService.setVisualService(wishTreeVisualService);
            cityWorldService.setWishTreeVisualService(wishTreeVisualService);
            guardianBeastService = new GuardianBeastService(this, guardianBeastConfig);
            if (isMockEnvironment()) {
                guardianVisualService = GuardianVisualService.unavailable("MockBukkit 不提供 ModelEngine 运行环境");
            } else {
                guardianModelBackend = new ModelEngineGuardianBackend(this);
                guardianBlueprintInstaller = new GuardianBlueprintInstaller(this, guardianModelBackend);
                guardianVisualService = new ModelEngineGuardianVisualService(this, guardianBeastService,
                        guardianBlueprintInstaller, guardianModelBackend);
                guardianModelBackend.setRegistrationCallback(() -> {
                    guardianBlueprintInstaller.refreshRegistration();
                    ((ModelEngineGuardianVisualService) guardianVisualService).reconcileLoadedWorlds();
                });
            }
            guardianBeastService.setVisualService(guardianVisualService);
            guardianContributionShopService = new GuardianContributionShopService(this, guardianBeastService, guardianShopConfig);
            citySocialService = new CitySocialService(this, citySocialConfig);
            cityWorldService.setGuardianVisualService(guardianVisualService);

            cityStateManager.loadCityStates();
            citySocialService.load();
            cityVisitTracker = new CityVisitTracker(this, citySocialService);
            citySocialService.setTracker(cityVisitTracker);
            cityStateLifecycleService.recover().whenComplete((count, error) -> {
                if (error != null) {
                    PluginLogger.error("城邦世界操作恢复失败，世界相关功能将保持锁定。",
                            error instanceof RuntimeException runtime ? runtime : new RuntimeException(error));
                } else if (count != null && count > 0) {
                    PluginLogger.info("已检查并恢复 " + count + " 个城邦世界操作。");
                }
            });
            requestManager.loadRequests();
            cacheCityStateManager.startTask();
            placeholderSnapshotService.refresh();

            PluginCommand command = Objects.requireNonNull(getCommand("citystate"), "plugin.yml 缺少 citystate 命令");
            CityStateCommand router = new CityStateCommand(this);
            command.setExecutor(router);
            command.setTabCompleter(router);

            registerListeners();
            runTasks();
            Bukkit.getScheduler().runTask(this, () -> wishTreeVisualService.prepareAssets()
                    .thenCompose(ignored -> cityWorldService.prepareBundledTemplate())
                    .thenCompose(ignored -> cityWorldService.validateTemplate())
                    .thenApply(valid -> {
                        if (Boolean.TRUE.equals(valid)) {
                            cityWorldService.recoverProtectionSyncs();
                            cityWorldService.reconcileLoadedWorlds();
                        }
                        return valid;
                    })
                    .exceptionally(error -> {
                        PluginLogger.error("许愿树结构或城邦模板校验失败，已禁止新建城邦。",
                                error instanceof RuntimeException runtime ? runtime : new RuntimeException(error));
                        return null;
                    }));
            Bukkit.getScheduler().runTask(this, () -> {
                if (guardianBlueprintInstaller != null) {
                    var status = guardianBlueprintInstaller.installMissing();
                    if (status.installedFiles() > 0 || !status.modelsRegistered()) {
                        PluginLogger.warning("公共灵兽蓝图已安装或尚未注册，请运行 /meg reload models 并部署 ModelEngine 资源包。");
                    }
                    if (guardianVisualService instanceof ModelEngineGuardianVisualService modelVisuals) {
                        modelVisuals.reconcileLoadedWorlds();
                    }
                }
            });
            if (MainSettings.isMetricsEnabled()) {
                new Metrics(this);
            }
            PluginLogger.info("已加载 " + cityStateManager.getCityStates().size() + " 个城邦和 "
                    + requestManager.getRequests().size() + " 个请求。");
        } catch (Throwable exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "独立城邦世界子系统启动失败，插件将停用。", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (cityStatePlayerManager != null) {
            for (CityStatePlayer player : cityStatePlayerManager.getOnlineCityStatePlayers()) {
                if (player.isUsingGUI()) {
                    player.closeInventory();
                }
            }
        }
        ChatInterceptor.unregisterAll(this);
        if (teleportService != null) teleportService.cancelAll();
        if (tpAllListener != null) tpAllListener.clear();
        if (placeholderSnapshotService != null) placeholderSnapshotService.clear();
        if (wishTreeVisualService != null) wishTreeVisualService.shutdown();
        if (guardianContributionShopService != null) guardianContributionShopService.shutdown();
        if (citySocialService != null) citySocialService.shutdown();
        if (guardianVisualService != null) guardianVisualService.shutdown();
        if (cityWorldService != null) cityWorldService.shutdown();
        if (placeholderAPIExpansion != null) {
            placeholderAPIExpansion.close();
            placeholderAPIExpansion = null;
        }
        Bukkit.getScheduler().cancelTasks(this);
        if (PluginLogger.isWriterEnabled()) {
            PluginLogger.closeWriters();
        }
        instance = null;
    }

    private boolean hookVault() {
        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            PluginLogger.error("Vault 未提供可用的经济服务，插件将停用。");
            return false;
        }
        vaultEconomy = new VaultEconomy(registration.getProvider());
        return true;
    }

    private void hookOptionalPlugins() {
        if (pluginManager.isPluginEnabled("PlaceholderAPI")) {
            PlaceholderAPIExpansion expansion = new PlaceholderAPIExpansion();
            if (!expansion.register()) {
                PluginLogger.error("PlaceholderAPI 扩展注册失败: rookiecitystate");
            } else {
                placeholderAPIExpansion = expansion;
            }
        }
        if (pluginManager.isPluginEnabled("PlayerPoints")) {
            PlayerPoints plugin = (PlayerPoints) pluginManager.getPlugin("PlayerPoints");
            if (plugin != null) {
                playerPointsEconomy = new PlayerPointsEconomy(plugin.getAPI());
            }
        }
    }

    private void registerListeners() {
        pluginManager.registerEvents(new GUIListener(), this);
        pluginManager.registerEvents(new MemberDamageListener(), this);
        pluginManager.registerEvents(new TeleportListener(teleportService), this);
        tpAllListener = new TpAllListener();
        pluginManager.registerEvents(tpAllListener, this);
        pluginManager.registerEvents(new PlayerListener(), this);
        pluginManager.registerEvents(new EssentialsChatListener(), this);
        pluginManager.registerEvents(new CityWorldAccessListener(cityWorldService), this);
        pluginManager.registerEvents(new CityProtectionSyncLockListener(cityWorldService), this);
        pluginManager.registerEvents(new CityWorldProtectionListener(cityWorldService), this);
        pluginManager.registerEvents(new WishTreeInteractionListener(this), this);
        if (guardianVisualService instanceof ModelEngineGuardianVisualService modelVisuals) {
            pluginManager.registerEvents(new GuardianInteractionListener(this, modelVisuals), this);
            pluginManager.registerEvents(guardianModelBackend, this);
        }
        if (cityVisitTracker != null) pluginManager.registerEvents(cityVisitTracker, this);
    }

    private void runTasks() {
        new LoggerSaveTask().runTaskTimer(this, 0L, 20L);
        new RequestCleanTask().runTaskTimer(this, 0L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(this, placeholderSnapshotService::refresh, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, cityWorldService::purgeExpiredArchives, 20L * 60L, 20L * 60L * 60L);
        Bukkit.getScheduler().runTaskTimer(this, citySocialService::refresh, 20L * 60L, 20L * 60L);
    }

    public boolean reloadPlugin() {
        try {
            loadConfigSnapshot();
        } catch (RuntimeException exception) {
            PluginLogger.error("配置重载失败，继续使用旧配置。", exception);
            getLogger().log(java.util.logging.Level.SEVERE, "配置重载失败，继续使用旧配置。", exception);
            return false;
        }
        ChatInterceptor.unregisterAll(this);
        teleportService.cancelAll(true);
        if (tpAllListener != null) tpAllListener.clear();
        placeholderSnapshotService.refresh();
        wishTreeService.reloadCatalog(wishTreeRewardsYaml);
        guardianBeastService.reloadConfig(guardianBeastConfig);
        guardianContributionShopService.reloadConfig(guardianShopConfig);
        citySocialService.reloadConfig(citySocialConfig);
        cityWorldService.prepareBundledTemplate().thenCompose(ignored -> cityWorldService.validateTemplate())
                .exceptionally(error -> {
                    PluginLogger.error("内置城邦模板准备或校验失败。",
                            error instanceof RuntimeException runtime ? runtime : new RuntimeException(error));
                    return false;
                });
        getCityStatePlayerManager().getOnlineCityStatePlayers().forEach(player -> {
            if (player.isUsingGUI()) {
                player.closeInventory();
                Util.sendMsg(player.getBukkitPlayer(), "&c配置已重载，当前 GUI 已关闭。");
            }
        });
        return true;
    }

    private void loadConfigSnapshot() {
        File defaults = new File(getDataFolder(), "defaults");
        File settingsFile = getConfigFile("settings.yml");
        boolean legacyMainPasteSetting = settingsFile.isFile()
                && !YamlFiles.load(settingsFile).contains("city_state.wish_tree.schematics.main.enabled");
        try {
            Files.createDirectories(defaults.toPath());
            File notice = new File(defaults, "请勿编辑此目录中的文件-这里只保存默认配置供参考");
            if (!notice.exists()) {
                Files.createFile(notice.toPath());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建默认配置目录", exception);
        }

        for (String fileName : BundledResourceCatalog.CONFIG_FILES) {
            installAndComplete("resources/" + fileName, getConfigFile(fileName),
                    new File(defaults, fileName + ".default"));
        }
        if (legacyMainPasteSetting) migrateLegacyTemplateCoordinates(settingsFile);
        for (String fileName : BundledResourceCatalog.GUI_FILES) {
            File target = getGUIFile(fileName);
            File defaultCopy = new File(defaults, "gui" + File.separator + fileName + ".default");
            if (fileName.equals("CityStateMineGUI.yml") || fileName.equals("GuardianBeastGUI.yml")
                    || fileName.equals("MainGUI.yml") || fileName.equals("CityStateInfoGUI.yml")) {
                installAndComplete("resources/gui/" + fileName, target, defaultCopy);
            } else {
                installResource("resources/gui/" + fileName, target, defaultCopy);
            }
        }
        for (String fileName : BundledResourceCatalog.SHOP_FILES) {
            installResource("resources/shop/" + fileName, getShopFile(fileName),
                    new File(defaults, "shop" + File.separator + fileName + ".default"));
        }

        YamlConfiguration nextSettings = YamlFiles.load(getConfigFile("settings.yml"));
        YamlConfiguration nextLang = YamlFiles.load(getConfigFile("lang.yml"));
        YamlConfiguration nextWishTreeRewards = YamlFiles.load(getConfigFile("wish_tree_rewards.yml"));
        YamlConfiguration nextGuardianYaml = YamlFiles.load(getConfigFile("guardian_beast.yml"));
        GuardianBeastConfig nextGuardianConfig = GuardianBeastConfig.load(nextGuardianYaml);
        GuardianShopConfig nextGuardianShopConfig = GuardianShopConfig.load(YamlFiles.load(getConfigFile("guardian_shop.yml")));
        CitySocialConfig nextCitySocialConfig = CitySocialConfig.load(YamlFiles.load(getConfigFile("city_social.yml")));
        Map<String, YamlConfiguration> nextGuis = new HashMap<>();
        for (String fileName : BundledResourceCatalog.GUI_FILES) {
            File file = getGUIFile(fileName);
            nextGuis.put(ResourceFiles.baseName(file), YamlFiles.load(file));
        }
        Map<String, Shop> nextShops = new HashMap<>();
        File shopFolder = new File(getDataFolder(), "config" + File.separator + "shop");
        File[] shopFiles = shopFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (shopFiles != null) {
            for (File file : shopFiles) {
                YamlConfiguration yaml = YamlFiles.load(file);
                String name = yaml.getString("name");
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("商店缺少 name: " + file.getAbsolutePath());
                }
                if (nextShops.put(name, new Shop(name, yaml)) != null) {
                    throw new IllegalArgumentException("商店名称重复: " + name);
                }
            }
        }

        validateConfiguration(nextSettings, nextGuis, nextShops);
        WishRewardCatalog.load(nextWishTreeRewards);
        SettingsLoader.load(nextSettings, MainSettings.class);
        langYaml = nextLang;
        wishTreeRewardsYaml = nextWishTreeRewards;
        guardianBeastConfig = nextGuardianConfig;
        guardianShopConfig = nextGuardianShopConfig;
        citySocialConfig = nextCitySocialConfig;
        guiYamlMap = Map.copyOf(nextGuis);
        shopYamlMap = Map.copyOf(nextShops);
    }

    private void migrateLegacyTemplateCoordinates(File settingsFile) {
        YamlConfiguration settings = YamlFiles.load(settingsFile);
        String templateName = settings.getString("city_state.world.template", "citystate_template");
        Path templateFolder = getServer().getWorldContainer().toPath().toAbsolutePath().normalize()
                .resolve(templateName).normalize();
        boolean existingCustomTemplate = Files.isDirectory(templateFolder)
                && !com.cuzz.rookiecitystate.world.BundledCityTemplateInstaller.markerMatches(templateFolder);
        if (existingCustomTemplate) {
            settings.set("city_state.wish_tree.schematics.main.enabled", true);
            YamlFiles.save(settings, settingsFile);
            PluginLogger.warning("检测到已有自定义模板，已保留旧版主城结构粘贴行为；如模板已包含主城请手动关闭 schematics.main.enabled。");
            return;
        }
        settings.set("city_state.wish_tree.schematics.main.enabled", false);
        if (settings.getInt("city_state.world.template_revision", 1) == 1) {
            settings.set("city_state.world.template_revision", 2);
        }
        if (settings.getInt("city_state.wish_tree.schematics.main.origin.y", 64) == 64) {
            settings.set("city_state.wish_tree.schematics.main.origin.y", 100);
        }
        if (settings.getDouble("city_state.wish_tree.schematics.main.spawn.x", 0.5D) == 0.5D
                && settings.getDouble("city_state.wish_tree.schematics.main.spawn.y", 65.0D) == 65.0D
                && settings.getDouble("city_state.wish_tree.schematics.main.spawn.z", 10.5D) == 10.5D) {
            settings.set("city_state.wish_tree.schematics.main.spawn.y", 101.0D);
            settings.set("city_state.wish_tree.schematics.main.spawn.z", 0.5D);
        }
        if (settings.getInt("city_state.wish_tree.schematics.tree.origin.y", 64) == 64) {
            settings.set("city_state.wish_tree.schematics.tree.origin.y", 100);
        }
        if (settings.getDouble("city_state.wish_tree.interaction.y", 65.0D) == 65.0D) {
            settings.set("city_state.wish_tree.interaction.y", 101.0D);
        }
        YamlFiles.save(settings, settingsFile);

        File guardianFile = getConfigFile("guardian_beast.yml");
        YamlConfiguration guardian = YamlFiles.load(guardianFile);
        if (guardian.getDouble("visual.anchor.y", 65.0D) == 65.0D) {
            guardian.set("visual.anchor.y", 101.0D);
            YamlFiles.save(guardian, guardianFile);
        }
        PluginLogger.warning("已迁移到内置城堡模板坐标并提升 template_revision=2；已有城邦世界不会被覆盖。");
    }

    private void validateConfiguration(YamlConfiguration settings,
                                       Map<String, YamlConfiguration> guis,
                                       Map<String, Shop> shops) {
        String regex = settings.getString("city_state.create.name_regex");
        try {
            Pattern.compile(Objects.requireNonNull(regex, "city_state.create.name_regex 缺失"));
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException("城邦名正则表达式无效", exception);
        }
        String materialName = settings.getString("city_state.icon.default.material");
        Material material = materialName == null ? null : Material.matchMaterial(materialName);
        if (material == null || material.isAir()) {
            throw new IllegalArgumentException("city_state.icon.default.material 无效: " + materialName);
        }
        String launcher = settings.getString("city_state.shop.launcher");
        if (launcher == null || !shops.containsKey(launcher)) {
            throw new IllegalArgumentException("引导商店不存在: " + launcher);
        }
        requireNonBlank(settings, "city_state.world.template");
        requireNonBlank(settings, "city_state.world.core_region");
        requireNonBlank(settings, "city_state.world.fallback_world");
        requireNonBlank(settings, "city_state.wish_tree.timezone");
        requireNonBlank(settings, "city_state.wish_tree.region_id");
        requireNonBlank(settings, "city_state.wish_tree.schematics.main.file");
        requireNonBlank(settings, "city_state.wish_tree.schematics.tree.file_pattern");
        try { java.time.ZoneId.of(settings.getString("city_state.wish_tree.timezone")); }
        catch (RuntimeException error) { throw new IllegalArgumentException("city_state.wish_tree.timezone 无效", error); }
        if (settings.getInt("city_state.wish_tree.reset_hour", -1) < 0
                || settings.getInt("city_state.wish_tree.reset_hour") > 23) {
            throw new IllegalArgumentException("city_state.wish_tree.reset_hour 必须为 0-23");
        }
        if (settings.getInt("city_state.world.template_revision", 0) < 1) {
            throw new IllegalArgumentException("city_state.world.template_revision 必须至少为 1");
        }
        if (settings.getInt("city_state.world.border_size", 0) < 16) {
            throw new IllegalArgumentException("city_state.world.border_size 必须至少为 16");
        }
        int coreMinX = settings.getInt("city_state.world.core_bounds.min.x");
        int coreMinY = settings.getInt("city_state.world.core_bounds.min.y");
        int coreMinZ = settings.getInt("city_state.world.core_bounds.min.z");
        int coreMaxX = settings.getInt("city_state.world.core_bounds.max.x");
        int coreMaxY = settings.getInt("city_state.world.core_bounds.max.y");
        int coreMaxZ = settings.getInt("city_state.world.core_bounds.max.z");
        if (coreMinX > coreMaxX || coreMinY > coreMaxY || coreMinZ > coreMaxZ) {
            throw new IllegalArgumentException("city_state.world.core_bounds 最小点不能大于最大点");
        }
        int halfBorder = settings.getInt("city_state.world.border_size") / 2;
        if (coreMinX < -halfBorder || coreMaxX >= halfBorder
                || coreMinZ < -halfBorder || coreMaxZ >= halfBorder) {
            throw new IllegalArgumentException("city_state.world.core_bounds 必须位于世界边界内");
        }
        double spawnX = settings.getDouble("city_state.wish_tree.schematics.main.spawn.x");
        double spawnZ = settings.getDouble("city_state.wish_tree.schematics.main.spawn.z");
        if (spawnX < -halfBorder || spawnX >= halfBorder || spawnZ < -halfBorder || spawnZ >= halfBorder) {
            throw new IllegalArgumentException("城邦出生点必须位于世界边界内");
        }
        if (settings.getInt("city_state.world.unload_delay_seconds", -1) < 0) {
            throw new IllegalArgumentException("city_state.world.unload_delay_seconds 不能为负数");
        }
        if (settings.getInt("city_state.world.archive_retention_days", 0) < 1) {
            throw new IllegalArgumentException("city_state.world.archive_retention_days 必须至少为 1");
        }
        String visibility = requireNonBlank(settings, "city_state.world.default_visibility");
        try {
            com.cuzz.rookiecitystate.world.WorldVisibility.valueOf(visibility.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("city_state.world.default_visibility 只能为 PRIVATE 或 PUBLIC");
        }
        guis.forEach((name, yaml) -> validateDeclaredRows("GUI " + name, yaml));
        shops.forEach((name, shop) -> validateDeclaredRows("商店 " + name, shop.getYaml()));
    }

    private String requireNonBlank(YamlConfiguration settings, String path) {
        String value = settings.getString(path);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(path + " 不能为空");
        return value.trim();
    }

    private void validateDeclaredRows(String source, YamlConfiguration yaml) {
        yaml.getKeys(true).stream()
                .filter(path -> path.equals("row") || path.endsWith(".row"))
                .forEach(path -> {
                    int rows = yaml.getInt(path, 0);
                    if (rows < 1 || rows > 6) {
                        throw new IllegalArgumentException(source + " 的 " + path + " 必须为 1-6");
                    }
                });
    }

    private void installAndComplete(String resource, File target, File defaultCopy) {
        installResource(resource, target, defaultCopy);
        YamlConfiguration current = YamlFiles.load(target);
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(getResource(resource), "资源不存在: " + resource), StandardCharsets.UTF_8)) {
            Set<String> changes = YamlFiles.completeMissing(current, YamlConfiguration.loadConfiguration(reader));
            if (!changes.isEmpty()) {
                YamlFiles.save(current, target);
                changes.forEach(path -> PluginLogger.warning("已补全配置 " + target.getName() + ": " + path));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法关闭配置资源: " + resource, exception);
        }
    }

    private void installResource(String resource, File target, File defaultCopy) {
        ResourceFiles.copy(this, resource, defaultCopy, true);
        ResourceFiles.copy(this, resource, target, false);
    }

    private File getConfigFolder() {
        return new File(getDataFolder(), "config");
    }

    private File getConfigFile(String fileName) {
        return new File(getConfigFolder(), fileName);
    }

    private File getGUIFile(String fileName) {
        return new File(new File(getConfigFolder(), "gui"), fileName);
    }

    public File getShopFile(@NotNull String fileName) {
        return new File(new File(getConfigFolder(), "shop"), fileName);
    }

    public Shop getShop(@NotNull String shopName) { return shopYamlMap.get(shopName); }
    public YamlConfiguration getLangYaml() { return langYaml; }
    public YamlConfiguration getGUIYaml(@NotNull String guiName) { return guiYamlMap.get(guiName); }
    public VaultEconomy getVaultEconomy() { return vaultEconomy; }
    public PlayerPointsEconomy getPlayerPointsEconomy() { return playerPointsEconomy; }
    public boolean isVaultEconomyHooked() { return vaultEconomy != null; }
    public boolean isPlayerPointsHooked() { return playerPointsEconomy != null; }
    public boolean isPlaceHolderAPIEnabled() { return pluginManager != null && pluginManager.isPluginEnabled("PlaceholderAPI"); }
    public CityStateManager getCityStateManager() { return cityStateManager; }
    public CityStatePlayerManager getCityStatePlayerManager() { return cityStatePlayerManager; }
    public CacheCityStateManager getCacheCityStateManager() { return cacheCityStateManager; }
    public RequestManager getRequestManager() { return requestManager; }
    public TransactionService getTransactionService() { return transactionService; }
    public TeleportService getTeleportService() { return teleportService; }
    public PlaceholderSnapshotService getPlaceholderSnapshotService() { return placeholderSnapshotService; }
    public CityWorldService getCityWorldService() { return cityWorldService; }
    public CityStateLifecycleService getCityStateLifecycleService() { return cityStateLifecycleService; }
    public WorldOperationStore getWorldOperationStore() { return worldOperationStore; }
    public WishTreeService getWishTreeService() { return wishTreeService; }
    public GuardianBeastService getGuardianBeastService() { return guardianBeastService; }
    public GuardianContributionShopService getGuardianContributionShopService() { return guardianContributionShopService; }
    public CitySocialService getCitySocialService() { return citySocialService; }
    public GuardianBlueprintInstaller getGuardianBlueprintInstaller() { return guardianBlueprintInstaller; }

    private boolean isMockEnvironment() {
        return getServer().getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("mockbukkit");
    }

    public static RookieCityState inst() { return instance; }
}
