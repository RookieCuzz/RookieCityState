package com.cuzz.rookiecitystate.listener;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.internal.text.LegacyText;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.placeholder.PlaceholderText;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.request.Request;
import com.cuzz.rookiecitystate.request.entities.TpAllRequest;
import com.cuzz.rookiecitystate.util.Util;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.UUID;

public final class TpAllListener implements Listener {
    private final RookieCityState plugin = RookieCityState.inst();
    private final TpAllPressTracker pressTracker = new TpAllPressTracker();

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        CityStatePlayer cityStatePlayer = plugin.getCityStatePlayerManager().getCityStatePlayer(player);
        if (!cityStatePlayer.isInCityState()) return;
        CityStateMember member = cityStatePlayer.getCityState().getMember(cityStatePlayer);
        if (member == null || !member.isValid()) return;

        TpAllRequest request = latestValidRequest(member);
        if (request == null) {
            pressTracker.remove(player.getUniqueId());
            return;
        }
        ConfigurationSection lang = plugin.getLangYaml().getConfigurationSection("TpAll");
        if (!MainSettings.getCityStateTpAllReceiveWorlds().contains(player.getWorld().getName())) {
            Util.sendMsg(player, lang.getString("no_receive_world"));
            return;
        }
        Location target = request.getLocation();
        if (target == null || target.getWorld() == null || !request.isValid()) {
            request.delete();
            pressTracker.remove(player.getUniqueId());
            Util.sendMsg(player, "&c该集结请求已经失效。");
            return;
        }

        long now = System.currentTimeMillis();
        int presses = pressTracker.press(player.getUniqueId(), request.getUuid(), now,
                MainSettings.getCityStateTpAllSneakCountInterval());
        int required = MainSettings.getCityStateTpAllSneakCount();
        if (presses < required) {
            PlaceholderContainer values = new PlaceholderContainer().add("count", required - presses);
            player.sendTitle(
                    LegacyText.getColoredText(PlaceholderText.replacePlaceholders(lang.getString("sneak_counter.title"), values)),
                    LegacyText.getColoredText(PlaceholderText.replacePlaceholders(lang.getString("sneak_counter.subtitle"), values)),
                    0, 20, 20);
            return;
        }

        plugin.getTeleportService().teleport(player, target,
                () -> {
                    if (plugin.getRequestManager().getRequest(request.getUuid()) == request) request.delete();
                    pressTracker.remove(player.getUniqueId());
                    player.sendTitle(LegacyText.getColoredText(lang.getString("teleported.title")),
                            LegacyText.getColoredText(lang.getString("teleported.subtitle")), 0, 20, 20);
                    Player sender = request.getSender().getCityStatePlayer().getBukkitPlayer();
                    if (sender != null) {
                        Util.sendMsg(sender, lang.getString("teleported.sender_msg"),
                                new PlaceholderContainer().add("member", player.getName()));
                    }
                },
                failure -> {
                    pressTracker.remove(player.getUniqueId());
                    Util.sendMsg(player, "&c传送失败: " + failure.getMessage());
                });
    }

    private TpAllRequest latestValidRequest(CityStateMember member) {
        TpAllRequest latest = null;
        for (Request raw : member.getReceivedRequests()) {
            if (!(raw instanceof TpAllRequest request)) continue;
            if (!request.isValid()) {
                request.delete();
                continue;
            }
            if (latest == null || request.getCreationTime() > latest.getCreationTime()) latest = request;
        }
        return latest;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pressTracker.remove(event.getPlayer().getUniqueId());
    }

    public void clear() { pressTracker.clear(); }
}
