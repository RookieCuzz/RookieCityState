package com.cuzz.rookiecitystate.listener;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.player.CityStatePlayerManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {
    private CityStatePlayerManager cityStatePlayerManager = RookieCityState.inst().getCityStatePlayerManager();

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        CityStatePlayer cityStatePlayer = cityStatePlayerManager.getCityStatePlayer(player);

        cityStatePlayer.setKnownName(player.getName());
    }
}
