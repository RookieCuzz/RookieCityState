package com.cuzz.rookiecitystate.listener;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.gui.entities.MainGUI;
import com.cuzz.rookiecitystate.internal.inventory.GuiHolder;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class GUIListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        CityStatePlayer cityStatePlayer = RookieCityState.inst().getCityStatePlayerManager().getCityStatePlayer(player);
        GUI gui = cityStatePlayer.getUsingGUI();
        if (gui == null) {
            player.closeInventory();
            return;
        }
        if (!gui.canUse()) {
            if (gui.canBack()) {
                gui.back();
            } else {
                new MainGUI(cityStatePlayer).open();
            }
            return;
        }
        holder.dispatch(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        CityStatePlayer cityStatePlayer = RookieCityState.inst().getCityStatePlayerManager().getCityStatePlayer(player);
        if (cityStatePlayer.isUsingGUI()) {
            cityStatePlayer.setUsingGUI(null);
        }
    }
}
