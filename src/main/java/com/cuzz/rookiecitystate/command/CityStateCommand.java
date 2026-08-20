package com.cuzz.rookiecitystate.command;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.gui.entities.MainGUI;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.logger.LoggerLevel;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.world.CityWorldView;
import com.cuzz.rookiecitystate.world.operation.CityWorldOperation;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class CityStateCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "rookiecitystate.admin";
    private final RookieCityState plugin;
    private final WishTreeCommandHandler wishTreeCommands;
    private final GuardianCommandHandler guardianCommands;
    private final SocialCommandHandler socialCommands;

    public CityStateCommand(RookieCityState plugin) {
        this.plugin = plugin;
        this.wishTreeCommands = new WishTreeCommandHandler(plugin);
        this.guardianCommands = new GuardianCommandHandler(plugin);
        this.socialCommands = new SocialCommandHandler(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (root.equals("gui") && sub.equals("main") && args.length == 2) {
            if (!(sender instanceof Player player)) {
                Util.sendMsg(sender, "&c该命令只能由玩家执行.");
                return true;
            }
            new MainGUI(plugin.getCityStatePlayerManager().getCityStatePlayer(player)).open();
            return true;
        }
        if (root.equals("world")) {
            return handleWorld(sender, args);
        }
        if (root.equals("wishtree")) {
            return wishTreeCommands.handle(sender, args);
        }
        if (root.equals("guardian")) {
            return guardianCommands.handle(sender, args);
        }
        if (root.equals("social")) {
            return socialCommands.handle(sender, args);
        }
        if (root.equals("archive") || root.equals("recovery") || root.equals("operation")) {
            String[] delegated = new String[args.length + 1];
            delegated[0] = "world";
            System.arraycopy(args, 0, delegated, 1, args.length);
            return handleWorld(sender, delegated);
        }
        if (root.equals("plugin") && sub.equals("version") && args.length == 2) {
            Util.sendMsg(sender, "&f插件版本: v" + plugin.getDescription().getVersion() + ".");
            Util.sendMsg(sender, "&f插件交流群: 786184610.");
            return true;
        }
        if (root.equals("plugin") && sub.equals("reload") && args.length == 2) {
            if (!requireAdmin(sender)) return true;
            if (plugin.reloadPlugin()) {
                Util.sendMsg(sender, "&f配置重载完毕.");
            } else {
                Util.sendMsg(sender, "&c配置重载失败，旧配置仍在使用；请检查控制台日志。");
            }
            return true;
        }
        if (root.equals("logger") && sub.equals("debug") && args.length == 2) {
            if (!requireAdmin(sender)) return true;
            LoggerLevel next = PluginLogger.getLevel() == LoggerLevel.DEBUG ? LoggerLevel.INFO : LoggerLevel.DEBUG;
            PluginLogger.setLevel(next);
            Util.sendMsg(sender, "当前 logger 级别: " + next.name() + ".");
            if (next == LoggerLevel.DEBUG) Util.sendMsg(sender, "&e这会在后台显示更多的信息来帮助你排查错误.");
            return true;
        }
        if (root.equals("helper") && sub.equals("getiteminfo") && args.length == 2) {
            if (!requireAdmin(sender)) return true;
            if (!(sender instanceof Player player)) {
                Util.sendMsg(sender, "&c该命令只能由玩家执行.");
                return true;
            }
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType().isAir()) {
                Util.sendMsg(sender, "&c物品不合法.");
                return true;
            }
            Util.sendMsg(sender, "&fmaterial = " + item.getType().name());
            if (item.getItemMeta() instanceof Damageable damageable) {
                Util.sendMsg(sender, "&fdamage = " + damageable.getDamage());
            }
            return true;
        }
        help(sender, label);
        return true;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (hasAdmin(sender)) return true;
        Util.sendMsg(sender, "&c无权限.");
        return false;
    }

    private boolean handleWorld(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (action.equals("exit") && args.length == 2) {
            if (!(sender instanceof Player player)) {
                Util.sendMsg(sender, "&c该命令只能由玩家执行。");
                return true;
            }
            complete(sender, plugin.getCityWorldService().exit(player), result ->
                    result.success() ? "&a已离开城邦世界。" : "&c离开失败: " + result.reason());
            return true;
        }
        if (!requireAdmin(sender)) return true;
        if (action.equals("reconcile") && args.length == 2) {
            complete(sender, plugin.getCityStateLifecycleService().recover(), count -> "&a已检查 " + count + " 个世界操作。");
            return true;
        }
        if ((action.equals("status") || action.equals("load") || action.equals("unload")
                || action.equals("provision")) && args.length == 3) {
            CityState cityState = plugin.getCityStateManager().getCityStateByName(args[2]);
            if (cityState == null) {
                try { cityState = plugin.getCityStateManager().getCityState(UUID.fromString(args[2])); }
                catch (IllegalArgumentException ignored) { }
            }
            if (cityState == null) {
                Util.sendMsg(sender, "&c城邦不存在。");
                return true;
            }
            CityState target = cityState;
            switch (action) {
                case "status" -> {
                    CityWorldView view = plugin.getCityWorldService().getWorldView(target);
                    Util.sendMsg(sender, "&f世界: " + view.worldName() + " 生命周期: " + view.lifecycleState()
                            + " 状态: " + view.worldState() + " 可见性: " + view.visibility()
                            + " 已加载: " + view.loaded());
                    if (view.lastError() != null) Util.sendMsg(sender, "&c错误: " + view.lastError());
                }
                case "load" -> complete(sender, plugin.getCityWorldService().ensureLoaded(target),
                        world -> "&a世界已加载: " + world.getName());
                case "unload" -> complete(sender, plugin.getCityWorldService().forceUnload(target.getWorldName()),
                        success -> success ? "&a世界已卸载。" : "&c世界卸载失败。");
                case "provision" -> complete(sender, plugin.getCityWorldService().provisionLegacy(target),
                        ignored -> "&a城邦世界已生成。");
            }
            return true;
        }
        if (action.equals("archive") && args.length >= 3) {
            if (args[2].equalsIgnoreCase("list") && args.length == 3) {
                List<String> archives = plugin.getCityWorldService().listArchives();
                Util.sendMsg(sender, archives.isEmpty() ? "&e没有可恢复归档。" : "&f归档: " + String.join(", ", archives));
                return true;
            }
            if (args[2].equalsIgnoreCase("restore") && args.length == 4) {
                complete(sender, plugin.getCityWorldService().restoreArchive(args[3]),
                        name -> "&a地图已恢复为管理员世界: " + name);
                return true;
            }
        }
        if (action.equals("recovery") && args.length == 4) {
            if (!plugin.getCityWorldService().isRecoveryWorld(args[3])) {
                Util.sendMsg(sender, "&c只能管理 rcs_recovery_ 前缀的恢复世界。");
                return true;
            }
            if (args[2].equalsIgnoreCase("unload")) {
                complete(sender, plugin.getCityWorldService().forceUnload(args[3]),
                        success -> success ? "&a恢复世界已卸载。" : "&c卸载失败。");
                return true;
            }
            if (args[2].equalsIgnoreCase("delete")) {
                complete(sender, plugin.getCityWorldService().deleteRecovery(args[3]),
                        success -> "&a恢复世界已删除。");
                return true;
            }
        }
        if (action.equals("operation") && args.length == 5 && args[2].equalsIgnoreCase("resolve")) {
            boolean charged;
            if (args[4].equalsIgnoreCase("charged")) charged = true;
            else if (args[4].equalsIgnoreCase("not_charged")) charged = false;
            else {
                Util.sendMsg(sender, "&c结论只能是 charged 或 not_charged。");
                return true;
            }
            try {
                complete(sender, plugin.getCityStateLifecycleService().resolvePayment(UUID.fromString(args[3]), charged),
                        message -> "&a" + message);
            } catch (IllegalArgumentException error) {
                Util.sendMsg(sender, "&c操作 ID 无效。");
            }
            return true;
        }
        if (action.equals("operation") && args.length == 3 && args[2].equalsIgnoreCase("list")) {
            List<CityWorldOperation> pending = plugin.getCityStateLifecycleService().getOperations().stream()
                    .filter(operation -> !operation.phase().equals("COMPLETE")).toList();
            if (pending.isEmpty()) {
                Util.sendMsg(sender, "&e没有未完成的世界操作。");
            } else {
                for (CityWorldOperation operation : pending) {
                    Util.sendMsg(sender, "&f" + operation.id() + " &7" + operation.kind() + "/"
                            + operation.phase() + " payment=" + operation.paymentState());
                }
            }
            return true;
        }
        Util.sendMsg(sender, "&c世界命令参数无效，使用 /cs 查看帮助。");
        return true;
    }

    private <T> void complete(CommandSender sender, CompletionStage<T> stage,
                              java.util.function.Function<T, String> success) {
        stage.whenComplete((value, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                Throwable root = error;
                while (root.getCause() != null && root instanceof java.util.concurrent.CompletionException) root = root.getCause();
                Util.sendMsg(sender, "&c操作失败: " + (root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage()));
            } else {
                Util.sendMsg(sender, success.apply(value));
            }
        }));
    }

    private void help(CommandSender sender, String label) {
        Util.sendMsg(sender, "&f/" + label + " gui main - 打开主界面");
        Util.sendMsg(sender, "&f/" + label + " world exit - 离开城邦世界");
        Util.sendMsg(sender, "&f/" + label + " social hot - 查看热门城邦");
        Util.sendMsg(sender, "&f/" + label + " plugin version - 插件版本");
        if (hasAdmin(sender)) {
            Util.sendMsg(sender, "&f/" + label + " plugin reload - 重载配置");
            Util.sendMsg(sender, "&f/" + label + " logger debug - 切换调试日志");
            Util.sendMsg(sender, "&f/" + label + " helper getItemInfo - 查看手中物品信息");
            Util.sendMsg(sender, "&f/" + label + " world status|load|unload|provision <城邦> - 管理城邦世界");
            Util.sendMsg(sender, "&f/" + label + " archive list|restore <归档ID> - 管理地图归档");
            Util.sendMsg(sender, "&f/" + label + " recovery unload|delete <世界> - 管理恢复世界");
            Util.sendMsg(sender, "&f/" + label + " operation list|resolve <ID> charged|not_charged - 处理异常扣款");
            Util.sendMsg(sender, "&f/" + label + " guardian ... - 管理公共灵兽");
            Util.sendMsg(sender, "&f/" + label + " social status|vote|reset|rebuild ... - 管理城邦社交");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> values = new ArrayList<>();
        if (args.length == 1) {
            values.add("gui");
            values.add("plugin");
            values.add("world");
            values.add("social");
            if (hasAdmin(sender)) {
                values.add("wishtree");
                values.add("guardian");
                values.add("logger");
                values.add("helper");
                values.addAll(List.of("archive", "recovery", "operation"));
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "gui" -> values.add("main");
                case "plugin" -> {
                    values.add("version");
                    if (hasAdmin(sender)) values.add("reload");
                }
                case "logger" -> { if (hasAdmin(sender)) values.add("debug"); }
                case "helper" -> { if (hasAdmin(sender)) values.add("getItemInfo"); }
                case "archive" -> { if (hasAdmin(sender)) values.addAll(List.of("list", "restore")); }
                case "recovery" -> { if (hasAdmin(sender)) values.addAll(List.of("unload", "delete")); }
                case "operation" -> { if (hasAdmin(sender)) values.addAll(List.of("list", "resolve")); }
                case "world" -> {
                    values.add("exit");
                    if (hasAdmin(sender)) values.addAll(List.of("status", "load", "unload", "provision",
                            "reconcile", "archive", "recovery", "operation"));
                }
                case "social" -> {
                    values.add("hot");
                    if (hasAdmin(sender)) values.addAll(List.of("status", "vote", "reset", "rebuild"));
                }
                case "wishtree" -> { if (hasAdmin(sender)) values.addAll(List.of("status", "reset", "grant", "claim", "visual")); }
                case "guardian" -> { if (hasAdmin(sender)) values.addAll(List.of("status", "reset", "set", "grant", "visual", "models", "shop")); }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("social") && hasAdmin(sender)) {
            if (args[1].equalsIgnoreCase("status")) values.addAll(List.of("city", "player"));
            else if (args[1].equalsIgnoreCase("vote")) values.add("revoke");
            else if (args[1].equalsIgnoreCase("reset")) values.addAll(List.of("recent", "all"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("social") && hasAdmin(sender)) {
            if ((args[1].equalsIgnoreCase("status") && args[2].equalsIgnoreCase("city"))
                    || args[1].equalsIgnoreCase("reset") || (args[1].equalsIgnoreCase("vote")
                    && args[2].equalsIgnoreCase("revoke"))) {
                plugin.getCityStateManager().getCityStates().forEach(city -> values.add(city.getName()));
            } else if (args[1].equalsIgnoreCase("status") && args[2].equalsIgnoreCase("player")) {
                plugin.getCityStatePlayerManager().getLoadedCityStatePlayers().forEach(player -> {
                    if (player.getName() != null) values.add(player.getName());
                });
            }
        } else if (args.length == 5 && args[0].equalsIgnoreCase("social") && hasAdmin(sender)) {
            if (args[1].equalsIgnoreCase("vote") && args[2].equalsIgnoreCase("revoke")) {
                plugin.getCityStatePlayerManager().getLoadedCityStatePlayers().forEach(player -> {
                    if (player.getName() != null) values.add(player.getName());
                });
            } else if (args[1].equalsIgnoreCase("reset")) values.add("confirm");
        } else if (args.length == 6 && args[0].equalsIgnoreCase("social")
                && args[1].equalsIgnoreCase("vote") && hasAdmin(sender)) {
            values.add(plugin.getCitySocialService().getConfig().clock().week(System.currentTimeMillis()));
        } else if (args.length == 7 && args[0].equalsIgnoreCase("social")
                && args[1].equalsIgnoreCase("vote") && hasAdmin(sender)) {
            values.add("confirm");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("world") && hasAdmin(sender)) {
            if (List.of("status", "load", "unload", "provision").contains(args[1].toLowerCase(Locale.ROOT))) {
                plugin.getCityStateManager().getCityStates().forEach(city -> values.add(city.getName()));
            } else if (args[1].equalsIgnoreCase("archive")) values.addAll(List.of("list", "restore"));
            else if (args[1].equalsIgnoreCase("recovery")) values.addAll(List.of("unload", "delete"));
            else if (args[1].equalsIgnoreCase("operation")) values.add("resolve");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("operation")
                && args[1].equalsIgnoreCase("resolve") && hasAdmin(sender)) {
            values.addAll(List.of("charged", "not_charged"));
        } else if (args.length == 5 && args[0].equalsIgnoreCase("world")
                && args[1].equalsIgnoreCase("operation") && args[2].equalsIgnoreCase("resolve") && hasAdmin(sender)) {
            values.addAll(List.of("charged", "not_charged"));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        values.removeIf(value -> !value.toLowerCase(Locale.ROOT).startsWith(prefix));
        return values;
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission(ADMIN);
    }
}
