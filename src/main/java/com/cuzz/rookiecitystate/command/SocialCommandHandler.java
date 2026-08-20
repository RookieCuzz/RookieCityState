package com.cuzz.rookiecitystate.command;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.gui.entities.MainGUI;
import com.cuzz.rookiecitystate.gui.entities.PopularCityStateGUI;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.util.Util;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.UUID;

final class SocialCommandHandler {
    private static final String ADMIN = "rookiecitystate.admin";
    private final RookieCityState plugin;

    SocialCommandHandler(RookieCityState plugin) { this.plugin = plugin; }

    boolean handle(CommandSender sender, String[] args) {
        try {
            if (args.length == 2 && word(args, 1, "hot")) {
                if (!(sender instanceof Player player)) {
                    Util.sendMsg(sender, "&c该命令只能由玩家执行。"); return true;
                }
                CityStatePlayer data = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
                new PopularCityStateGUI(new MainGUI(data), data).open();
                return true;
            }
            if (!sender.hasPermission(ADMIN)) { Util.sendMsg(sender, "&c无权限。"); return true; }
            if (args.length == 4 && word(args, 1, "status") && word(args, 2, "city")) {
                CityState city = city(args[3]);
                var view = plugin.getCitySocialService().getView(null, city);
                Util.sendMsg(sender, "&f社交状态 " + city.getName() + " &7status=" + view.status()
                        + " total=" + view.totalLikes() + " visitors7d=" + view.recentVisitors()
                        + " likes7d=" + view.recentLikes() + " hot=" + view.hotScore() + " rank=" + view.hotRank());
                if (view.error() != null) Util.sendMsg(sender, "&c错误: " + view.error());
                return true;
            }
            if (args.length == 4 && word(args, 1, "status") && word(args, 2, "player")) {
                CityStatePlayer player = player(args[3]);
                var status = plugin.getCitySocialService().getPlayerStatus(player.getUuid());
                Util.sendMsg(sender, "&f社交玩家 " + player.getName() + " &7week=" + status.week()
                        + " votes=" + status.votesUsed() + "/" + (status.votesUsed() + status.votesRemaining())
                        + " qualified=" + status.qualifiedCities() + " liked=" + status.likedCities());
                return true;
            }
            if (args.length == 7 && word(args, 1, "vote") && word(args, 2, "revoke") && confirm(args[6])) {
                CityState city = city(args[3]);
                CityStatePlayer player = player(args[4]);
                String week = LocalDate.parse(args[5]).toString();
                boolean changed = plugin.getCitySocialService().revoke(city, player.getUuid(), week);
                audit(sender, "vote revoke", city.getUuid() + ":" + player.getUuid() + ":" + week);
                Util.sendMsg(sender, changed ? "&a已备份并撤销该周点赞。" : "&e没有找到对应点赞。");
                return true;
            }
            if (args.length == 5 && word(args, 1, "reset") && confirm(args[4])) {
                CityState city = city(args[3]);
                if (word(args, 2, "recent")) plugin.getCitySocialService().resetRecent(city);
                else if (word(args, 2, "all")) plugin.getCitySocialService().resetAll(city);
                else throw new IllegalArgumentException("重置类型只能为 recent 或 all");
                audit(sender, "reset " + args[2].toLowerCase(java.util.Locale.ROOT), city.getUuid().toString());
                Util.sendMsg(sender, "&a已备份并重置城邦社交数据。");
                return true;
            }
            if (args.length == 2 && word(args, 1, "rebuild")) {
                plugin.getCitySocialService().rebuild();
                audit(sender, "rebuild", "all");
                Util.sendMsg(sender, "&a社交索引与热门榜已重建。");
                return true;
            }
            help(sender);
        } catch (RuntimeException error) { Util.sendMsg(sender, "&c操作失败: " + error.getMessage()); }
        return true;
    }

    private CityState city(String value) {
        CityState city = plugin.getCityStateManager().getCityStateByName(value);
        if (city == null) try { city = plugin.getCityStateManager().getCityState(UUID.fromString(value)); }
        catch (IllegalArgumentException ignored) { }
        if (city == null) throw new IllegalArgumentException("城邦不存在");
        return city;
    }

    private CityStatePlayer player(String value) {
        CityStatePlayer player = plugin.getCityStatePlayerManager().findRegisteredPlayer(value);
        if (player == null) throw new IllegalArgumentException("玩家数据不存在");
        return player;
    }

    private boolean word(String[] args, int index, String expected) { return args[index].equalsIgnoreCase(expected); }
    private boolean confirm(String value) {
        if (!value.equalsIgnoreCase("confirm")) throw new IllegalArgumentException("破坏性操作必须以 confirm 确认");
        return true;
    }
    private void audit(CommandSender sender, String action, String target) {
        PluginLogger.info("SOCIAL_ADMIN actor=" + sender.getName() + " action=" + action + " target=" + target);
    }
    private void help(CommandSender sender) {
        Util.sendMsg(sender, "&f/cs social hot");
        if (!sender.hasPermission(ADMIN)) return;
        Util.sendMsg(sender, "&f/cs social status city|player <目标>");
        Util.sendMsg(sender, "&f/cs social vote revoke <城邦> <玩家> <weekCycle> confirm");
        Util.sendMsg(sender, "&f/cs social reset recent|all <城邦> confirm");
        Util.sendMsg(sender, "&f/cs social rebuild");
    }
}
