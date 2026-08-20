package com.cuzz.rookiecitystate.command;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.guardian.GuardianBeastState;
import com.cuzz.rookiecitystate.guardian.GuardianModelInstallStatus;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.UUID;

final class GuardianCommandHandler {
    private final RookieCityState plugin;
    GuardianCommandHandler(RookieCityState plugin) { this.plugin = plugin; }

    boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rookiecitystate.admin")) { Util.sendMsg(sender, "&c无权限。"); return true; }
        try {
            if (args.length >= 2 && word(args, 1, "shop")) return handleShop(sender, args);
            if (args.length == 4 && word(args, 1, "status") && word(args, 2, "city")) {
                CityState city = city(args[3]);
                GuardianBeastState state = plugin.getGuardianBeastService().state(city);
                var config = plugin.getGuardianBeastService().getConfig();
                state.ensureDay(config.day(System.currentTimeMillis()), config, new java.util.SplittableRandom());
                Util.sendMsg(sender, "&f灵兽 " + city.getName() + " &7species=" + state.species()
                        + " level=" + config.level(state.completedDays()) + " form=" + config.form(state.completedDays())
                        + " days=" + state.completedDays());
                Util.sendMsg(sender, "&7daily=" + state.day() + " fullness=" + state.fullness() + "/" + state.target()
                        + " completed=" + state.completedToday() + " visual_error=" + state.visualError());
                return true;
            }
            if (args.length == 4 && word(args, 1, "status") && word(args, 2, "player")) {
                CityStatePlayer player = player(args[3]);
                long[] values = plugin.getGuardianBeastService().playerContribution(player);
                Util.sendMsg(sender, "&f灵兽玩家 " + player.getName() + " &7available=" + values[0]
                        + " lifetime=" + values[1] + " feeds_today=" + values[2]);
                return true;
            }
            if (args.length == 5 && word(args, 1, "reset") && word(args, 2, "daily") && confirm(args[4])) {
                CityState city = city(args[3]);
                plugin.getGuardianBeastService().resetDaily(city);
                audit(sender, "reset daily", city.getUuid().toString());
                Util.sendMsg(sender, "&a已备份并重置该城邦的每日喂养进度。");
                return true;
            }
            if (args.length == 5 && word(args, 1, "reset") && word(args, 2, "species") && confirm(args[4])) {
                CityState city = city(args[3]);
                plugin.getGuardianBeastService().resetSpecies(city);
                audit(sender, "reset species", city.getUuid().toString());
                Util.sendMsg(sender, "&a已备份并重置灵兽种类、完成日与每日进度。");
                return true;
            }
            if (args.length == 6 && word(args, 1, "set") && word(args, 2, "days") && confirm(args[5])) {
                CityState city = city(args[3]);
                int days = boundedInt(args[4], 0, 53, "完成日");
                plugin.getGuardianBeastService().setDays(city, days);
                audit(sender, "set days", city.getUuid() + " -> " + days);
                Util.sendMsg(sender, "&a已设置累计喂满日并请求刷新外观。");
                return true;
            }
            if (args.length == 5 && word(args, 1, "grant") && word(args, 2, "contribution")) {
                CityStatePlayer player = player(args[3]);
                long amount = boundedLong(args[4], 1, 1_000_000_000L, "贡献");
                plugin.getGuardianBeastService().grantContribution(player, amount);
                audit(sender, "grant contribution", player.getUuid() + " +" + amount);
                Util.sendMsg(sender, "&a已补发可用与累计贡献各 " + amount + " 点。");
                return true;
            }
            if (args.length == 4 && word(args, 1, "visual") && word(args, 2, "retry")) {
                CityState city = city(args[3]);
                plugin.getGuardianBeastService().retryVisual(city).whenComplete((ignored, error) ->
                        Bukkit.getScheduler().runTask(plugin, () -> Util.sendMsg(sender,
                                error == null ? "&a灵兽视觉已恢复。" : "&c视觉恢复失败: " + root(error))));
                return true;
            }
            if (args.length == 3 && word(args, 1, "models") && word(args, 2, "status")) {
                modelStatus(sender, plugin.getGuardianBlueprintInstaller().status());
                return true;
            }
            if (args.length == 3 && word(args, 1, "models") && word(args, 2, "install")) {
                GuardianModelInstallStatus status = plugin.getGuardianBlueprintInstaller().installMissing();
                modelStatus(sender, status);
                if (status.installedFiles() > 0) Util.sendMsg(sender, "&e请运行 /meg reload models，并部署 ModelEngine 生成的资源包。");
                return true;
            }
            help(sender);
        } catch (RuntimeException error) {
            Util.sendMsg(sender, "&c操作失败: " + error.getMessage());
        }
        return true;
    }

    private boolean handleShop(CommandSender sender, String[] args) {
        var shop = plugin.getGuardianContributionShopService();
        if (args.length == 3 && word(args, 2, "status")) {
            var rotation = shop.getCurrentRotation();
            Util.sendMsg(sender, "&f灵兽贡献商店 &7cycle=" + rotation.cycle() + " seed=" + rotation.seed()
                    + " revision=" + rotation.configRevision());
            Util.sendMsg(sender, "&7products=" + rotation.products().stream().map(product -> product.id()
                    + "(" + product.price() + ")").toList());
            return true;
        }
        if (args.length == 5 && word(args, 2, "status") && word(args, 3, "player")) {
            CityStatePlayer player = player(args[4]);
            long[] values = plugin.getGuardianBeastService().playerContribution(player);
            Util.sendMsg(sender, "&f灵兽商店玩家 " + player.getName() + " &7available=" + values[0]
                    + " lifetime=" + values[1] + " owned=" + shop.owned(player).keySet());
            return true;
        }
        if (args.length == 4 && word(args, 2, "rotate") && confirm(args[3])) {
            var rotation = shop.rotateNow();
            audit(sender, "shop rotate", rotation.cycle() + ":" + rotation.seed());
            Util.sendMsg(sender, "&a已备份并重抽本周六个商品。");
            return true;
        }
        if (args.length == 6 && word(args, 2, "grant") && word(args, 3, "product")) {
            CityStatePlayer player = player(args[4]);
            shop.grantProduct(player, args[5]);
            audit(sender, "shop grant product", player.getUuid() + ":" + args[5]);
            Util.sendMsg(sender, "&a已补发永久商品。");
            return true;
        }
        if (args.length == 7 && word(args, 2, "revoke") && word(args, 3, "product") && confirm(args[6])) {
            CityStatePlayer player = player(args[4]);
            shop.revokeProduct(player, args[5]);
            audit(sender, "shop revoke product", player.getUuid() + ":" + args[5]);
            Util.sendMsg(sender, "&a已备份并撤销永久商品。");
            return true;
        }
        if (args.length == 6 && word(args, 2, "reset") && word(args, 3, "limits") && confirm(args[5])) {
            CityStatePlayer player = player(args[4]);
            shop.resetLimits(player);
            audit(sender, "shop reset limits", player.getUuid().toString());
            Util.sendMsg(sender, "&a已备份并重置玩家商店限购。");
            return true;
        }
        Util.sendMsg(sender, "&f/cs guardian shop status [player <玩家>]");
        Util.sendMsg(sender, "&f/cs guardian shop rotate confirm");
        Util.sendMsg(sender, "&f/cs guardian shop grant product <玩家> <商品ID>");
        Util.sendMsg(sender, "&f/cs guardian shop revoke product <玩家> <商品ID> confirm");
        Util.sendMsg(sender, "&f/cs guardian shop reset limits <玩家> confirm");
        return true;
    }

    private void modelStatus(CommandSender sender, GuardianModelInstallStatus status) {
        Util.sendMsg(sender, "&f灵兽模型 &7assets=" + status.assetsValid() + " registered=" + status.modelsRegistered()
                + " installed_now=" + status.installedFiles());
        status.errors().forEach(error -> Util.sendMsg(sender, "&c- " + error));
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

    private int boundedInt(String value, int min, int max, String name) {
        long number = boundedLong(value, min, max, name);
        return (int) number;
    }

    private long boundedLong(String value, long min, long max, String name) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < min || parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException error) { throw new IllegalArgumentException(name + "必须为 " + min + "-" + max + " 的整数"); }
    }

    private boolean word(String[] args, int index, String expected) { return args[index].equalsIgnoreCase(expected); }
    private boolean confirm(String value) {
        if (!value.equalsIgnoreCase("confirm")) throw new IllegalArgumentException("破坏性操作必须以 confirm 确认");
        return true;
    }
    private String root(Throwable error) { while (error.getCause() != null) error = error.getCause(); return String.valueOf(error.getMessage()); }
    private void audit(CommandSender sender, String action, String target) {
        PluginLogger.info("GUARDIAN_ADMIN actor=" + sender.getName() + " action=" + action + " target=" + target);
    }
    private void help(CommandSender sender) {
        Util.sendMsg(sender, "&f/cs guardian status city|player <目标>");
        Util.sendMsg(sender, "&f/cs guardian reset daily|species <城邦> confirm");
        Util.sendMsg(sender, "&f/cs guardian set days <城邦> <0-53> confirm");
        Util.sendMsg(sender, "&f/cs guardian grant contribution <玩家> <数量>");
        Util.sendMsg(sender, "&f/cs guardian visual retry <城邦>");
        Util.sendMsg(sender, "&f/cs guardian models status|install");
        Util.sendMsg(sender, "&f/cs guardian shop status|rotate|grant|revoke|reset ...");
    }
}
