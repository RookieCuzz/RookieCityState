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
import com.cuzz.rookiecitystate.listener.EssentialsChatListener;
import com.cuzz.rookiecitystate.listener.GUIListener;
import com.cuzz.rookiecitystate.listener.MemberDamageListener;
import com.cuzz.rookiecitystate.listener.PlayerListener;
import com.cuzz.rookiecitystate.listener.TeleportListener;
import com.cuzz.rookiecitystate.listener.TpAllListener;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RookieCityState extends JavaPlugin {
    private static final String[] GUI_RESOURCES = {
            "CityStateCreateGUI.yml", "CityStateInfoGUI.yml", "CityStateMemberListGUI.yml",
            "CityStateMineGUI.yml", "CityStateDonateGUI.yml", "CityStateJoinCheckGUI.yml",
            "CityStateMemberManageGUI.yml", "CityStateIconRepositoryGUI.yml", "MainGUI.yml"
    };
    private static final String[] CONFIG_RESOURCES = {"settings.yml", "lang.yml"};
    private static final String[] SHOP_RESOURCES = {"Shop1.yml", "Shop2.yml"};

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

    private YamlConfiguration langYaml;
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

        cityStateManager.loadCityStates();
        requestManager.loadRequests();
        cacheCityStateManager.startTask();
        placeholderSnapshotService.refresh();

        PluginCommand command = Objects.requireNonNull(getCommand("citystate"), "plugin.yml 缺少 citystate 命令");
        CityStateCommand router = new CityStateCommand(this);
        command.setExecutor(router);
        command.setTabCompleter(router);

        registerListeners();
        runTasks();
        if (MainSettings.isMetricsEnabled()) {
            new Metrics(this);
        }
        PluginLogger.info("已加载 " + cityStateManager.getCityStates().size() + " 个城邦和 "
                + requestManager.getRequests().size() + " 个请求。");
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
    }

    private void runTasks() {
        new LoggerSaveTask().runTaskTimer(this, 0L, 20L);
        new RequestCleanTask().runTaskTimer(this, 0L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(this, placeholderSnapshotService::refresh, 20L, 20L);
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
        teleportService.cancelAll();
        if (tpAllListener != null) tpAllListener.clear();
        placeholderSnapshotService.refresh();
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
        try {
            Files.createDirectories(defaults.toPath());
            File notice = new File(defaults, "请勿编辑此目录中的文件-这里只保存默认配置供参考");
            if (!notice.exists()) {
                Files.createFile(notice.toPath());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建默认配置目录", exception);
        }

        for (String fileName : CONFIG_RESOURCES) {
            installAndComplete("resources/" + fileName, getConfigFile(fileName),
                    new File(defaults, fileName + ".default"));
        }
        for (String fileName : GUI_RESOURCES) {
            installResource("resources/gui/" + fileName, getGUIFile(fileName),
                    new File(defaults, "gui" + File.separator + fileName + ".default"));
        }
        for (String fileName : SHOP_RESOURCES) {
            installResource("resources/shop/" + fileName, getShopFile(fileName),
                    new File(defaults, "shop" + File.separator + fileName + ".default"));
        }

        YamlConfiguration nextSettings = YamlFiles.load(getConfigFile("settings.yml"));
        YamlConfiguration nextLang = YamlFiles.load(getConfigFile("lang.yml"));
        Map<String, YamlConfiguration> nextGuis = new HashMap<>();
        for (String fileName : GUI_RESOURCES) {
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
        SettingsLoader.load(nextSettings, MainSettings.class);
        langYaml = nextLang;
        guiYamlMap = Map.copyOf(nextGuis);
        shopYamlMap = Map.copyOf(nextShops);
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
        guis.forEach((name, yaml) -> validateDeclaredRows("GUI " + name, yaml));
        shops.forEach((name, shop) -> validateDeclaredRows("商店 " + name, shop.getYaml()));
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

    public static RookieCityState inst() { return instance; }
}
