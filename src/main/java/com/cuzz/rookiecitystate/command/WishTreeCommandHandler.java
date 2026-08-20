package com.cuzz.rookiecitystate.command;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.wishtree.WishPlayerAdminView;
import com.cuzz.rookiecitystate.wishtree.WishQuality;
import com.cuzz.rookiecitystate.wishtree.WishTreeAdminView;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

final class WishTreeCommandHandler {
    private final RookieCityState plugin;

    WishTreeCommandHandler(RookieCityState plugin) { this.plugin = plugin; }

    boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rookiecitystate.admin")) {
            Util.sendMsg(sender, "&c无权限。");
            return true;
        }
        try {
            if (args.length == 4 && word(args, 1, "status")) {
                if (word(args, 2, "city")) return statusCity(sender, city(args[3]));
                if (word(args, 2, "player")) return statusPlayer(sender, player(args[3]));
            }
            if (args.length == 5 && word(args, 1, "reset") && word(args, 2, "weekly") && confirm(args[4])) {
                CityState city = city(args[3]);
                plugin.getWishTreeService().resetWeekly(city);
                audited(sender, "reset weekly", city.getUuid().toString());
                Util.sendMsg(sender, "&a已重置城邦本周许愿树进度。");
                return true;
            }
            if (args.length == 6 && word(args, 1, "reset") && word(args, 2, "level") && confirm(args[5])) {
                CityState city = city(args[3]);
                int level = boundedInt(args[4], 1, 5, "等级");
                audited(sender, "reset level", city.getUuid() + " -> " + level);
                complete(sender, plugin.getWishTreeService().resetLevel(city, level), "&a等级与建筑已重置。");
                return true;
            }
            if (args.length == 5 && word(args, 1, "reset") && word(args, 2, "daily") && confirm(args[4])) {
                CityStatePlayer player = player(args[3]);
                plugin.getWishTreeService().resetDaily(player);
                audited(sender, "reset daily", player.getUuid().toString());
                Util.sendMsg(sender, "&a已重置玩家每日许愿数据。");
                return true;
            }
            if (args.length == 6 && word(args, 1, "reset") && word(args, 2, "pity") && confirm(args[5])) {
                CityStatePlayer player = player(args[3]);
                Set<WishQuality> qualities = switch (args[4].toLowerCase(Locale.ROOT)) {
                    case "rare" -> Set.of(WishQuality.RARE);
                    case "epic" -> Set.of(WishQuality.EPIC);
                    case "all" -> Set.of(WishQuality.RARE, WishQuality.EPIC);
                    default -> throw new IllegalArgumentException("品质只能是 rare|epic|all");
                };
                plugin.getWishTreeService().resetPity(player, qualities);
                audited(sender, "reset pity", player.getUuid() + " " + args[4]);
                Util.sendMsg(sender, "&a已重置对应品质保底。");
                return true;
            }
            if (args.length == 5 && word(args, 1, "grant") && word(args, 2, "stones")) {
                CityStatePlayer player = player(args[3]);
                int amount = boundedInt(args[4], 1, 1_000_000, "数量");
                plugin.getWishTreeService().grantStones(player, amount);
                audited(sender, "grant stones", player.getUuid() + " +" + amount);
                Util.sendMsg(sender, "&a已发放 " + amount + " 颗魔力石。");
                return true;
            }
            if ((args.length == 5 || args.length == 6) && word(args, 1, "grant") && word(args, 2, "reward")) {
                CityStatePlayer player = player(args[3]);
                int count = args.length == 6 ? boundedInt(args[5], 1, 100, "数量") : 1;
                CityState current = player.getCityState();
                List<UUID> ids = plugin.getWishTreeService().grantReward(player, args[4], count,
                        current == null ? null : current.getUuid());
                audited(sender, "grant reward", player.getUuid() + " " + args[4] + " x" + count);
                Util.sendMsg(sender, "&a已补发奖励，记录数: " + ids.size());
                return true;
            }
            if (args.length == 6 && word(args, 1, "claim") && word(args, 2, "resolve")) {
                CityStatePlayer player = player(args[3]);
                UUID claimId = UUID.fromString(args[4]);
                boolean delivered;
                if (word(args, 5, "delivered")) delivered = true;
                else if (word(args, 5, "retry")) delivered = false;
                else throw new IllegalArgumentException("结论只能是 delivered|retry");
                plugin.getWishTreeService().resolveClaim(player, claimId, delivered);
                audited(sender, "resolve claim", player.getUuid() + " " + claimId + " " + args[5]);
                Util.sendMsg(sender, delivered ? "&a已标记为已发放。" : "&a已恢复为可重试。");
                return true;
            }
            if (args.length == 4 && word(args, 1, "visual") && word(args, 2, "retry")) {
                CityState city = city(args[3]);
                audited(sender, "visual retry", city.getUuid().toString());
                complete(sender, plugin.getWishTreeService().retryVisual(city), "&a许愿树视觉修复已完成。");
                return true;
            }
            help(sender);
        } catch (RuntimeException error) {
            Util.sendMsg(sender, "&c操作失败: " + error.getMessage());
        }
        return true;
    }

    private boolean statusCity(CommandSender sender, CityState city) {
        WishTreeAdminView view = plugin.getWishTreeService().getAdminView(city);
        Util.sendMsg(sender, "&f许愿树 " + city.getName() + " &7level=" + view.level() + " xp=" + view.experience()
                + " visual=" + view.visualLevel() + "/" + view.visualState());
        Util.sendMsg(sender, "&7week=" + view.week() + " growth=" + view.growth() + "/" + view.target()
                + " participants=" + view.participants() + " unlocked=" + view.unlocked());
        if (view.visualError() != null) Util.sendMsg(sender, "&cvisual_error=" + view.visualError());
        return true;
    }

    private boolean statusPlayer(CommandSender sender, CityStatePlayer player) {
        WishPlayerAdminView view = plugin.getWishTreeService().getAdminView(player);
        Util.sendMsg(sender, "&f许愿玩家 " + player.getName() + " &7stones=" + view.magicStones()
                + " target=" + view.targetId() + " pity=" + view.rarePity() + "/" + view.epicPity());
        Util.sendMsg(sender, "&7daily=" + view.freeUsed() + "+" + view.paidUsed()
                + " inbox=" + view.pendingClaims() + " ambiguous=" + view.ambiguousClaims());
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

    private int boundedInt(String value, int minimum, int maximum, String name) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(name + "必须为 " + minimum + "-" + maximum + " 的整数");
        }
    }

    private void complete(CommandSender sender, CompletionStage<Void> stage, String success) {
        stage.whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error == null) Util.sendMsg(sender, success);
            else Util.sendMsg(sender, "&c操作失败: " + rootMessage(error));
        }));
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private void audited(CommandSender sender, String action, String target) {
        PluginLogger.info("WISH_TREE_ADMIN actor=" + sender.getName() + " action=" + action + " target=" + target);
    }
    private boolean word(String[] args, int index, String expected) { return args[index].equalsIgnoreCase(expected); }
    private boolean confirm(String value) {
        if (!value.equalsIgnoreCase("confirm")) throw new IllegalArgumentException("破坏性重置必须以 confirm 确认");
        return true;
    }
    private void help(CommandSender sender) {
        Util.sendMsg(sender, "&f/cs wishtree status city|player <目标>");
        Util.sendMsg(sender, "&f/cs wishtree reset weekly|level|daily|pity ... confirm");
        Util.sendMsg(sender, "&f/cs wishtree grant stones|reward ...");
        Util.sendMsg(sender, "&f/cs wishtree claim resolve <玩家> <claimId> delivered|retry");
        Util.sendMsg(sender, "&f/cs wishtree visual retry <城邦>");
    }
}
