package com.cuzz.rookiecitystate.listener;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.internal.text.LegacyText;
import com.cuzz.rookiecitystate.teleport.TeleportService;
import com.cuzz.rookiecitystate.util.Util;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TeleportListener implements Listener {
    private final TeleportService service;

    public TeleportListener(TeleportService service) {
        this.service = service;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        service.handleMove(player, event.getFrom(), event.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.cancel(event.getPlayer(), true);
    }

    public void notifyCancelled(Player player) {
        ConfigurationSection lang = RookieCityState.inst().getLangYaml();
        player.sendTitle(
                LegacyText.getColoredText(lang.getString("CityStateMineGUI.city_state_spawn.cancelled.title")),
                LegacyText.getColoredText(lang.getString("CityStateMineGUI.city_state_spawn.cancelled.subtitle")), 0, 20, 20);
        String message = lang.getString("CityStateMineGUI.city_state_spawn.cancelled.msg");
        if (message != null && !message.isBlank()) Util.sendMsg(player, message);
    }
}
