package com.cuzz.rookiecitystate.command;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.gui.entities.MainGUI;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.logger.LoggerLevel;
import com.cuzz.rookiecitystate.util.Util;
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

public final class CityStateCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "rookiecitystate.admin";
    private final RookieCityState plugin;

    public CityStateCommand(RookieCityState plugin) {
        this.plugin = plugin;
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

    private void help(CommandSender sender, String label) {
        Util.sendMsg(sender, "&f/" + label + " gui main - 打开主界面");
        Util.sendMsg(sender, "&f/" + label + " plugin version - 插件版本");
        if (hasAdmin(sender)) {
            Util.sendMsg(sender, "&f/" + label + " plugin reload - 重载配置");
            Util.sendMsg(sender, "&f/" + label + " logger debug - 切换调试日志");
            Util.sendMsg(sender, "&f/" + label + " helper getItemInfo - 查看手中物品信息");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> values = new ArrayList<>();
        if (args.length == 1) {
            values.add("gui");
            values.add("plugin");
            if (hasAdmin(sender)) {
                values.add("logger");
                values.add("helper");
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
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        values.removeIf(value -> !value.toLowerCase(Locale.ROOT).startsWith(prefix));
        return values;
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission(ADMIN);
    }
}
