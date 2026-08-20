package com.cuzz.rookiecitystate.listener;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.gui.entities.GuardianBeastGUI;
import com.cuzz.rookiecitystate.guardian.FeedResult;
import com.cuzz.rookiecitystate.guardian.ModelEngineGuardianVisualService;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.util.Util;
import org.bukkit.entity.Interaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

public final class GuardianInteractionListener implements Listener {
    private final RookieCityState plugin;
    private final ModelEngineGuardianVisualService visuals;

    public GuardianInteractionListener(RookieCityState plugin, ModelEngineGuardianVisualService visuals) {
        this.plugin = plugin;
        this.visuals = visuals;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Interaction interaction)
                || !visuals.isInteraction(interaction)) return;
        event.setCancelled(true);
        UUID cityId = visuals.cityId(interaction);
        CityState city = cityId == null ? null : plugin.getCityStateManager().getCityState(cityId);
        if (city == null || !city.isWorldReady()) {
            Util.sendMsg(event.getPlayer(), "&c公共灵兽当前不可用。");
            return;
        }
        boolean validFood = plugin.getGuardianBeastService().getConfig()
                .food(event.getPlayer().getInventory().getItemInMainHand().getType()) != null;
        if (validFood && city.getMember(event.getPlayer().getUniqueId()) != null) {
            plugin.getGuardianBeastService().feed(event.getPlayer(), city, event.getHand()).thenAccept(result -> {
                String color = result.status() == FeedResult.Status.SUCCESS ? "&a" : "&c";
                Util.sendMsg(event.getPlayer(), color + result.message()
                        + (result.status() == FeedResult.Status.SUCCESS ? " &7(贡献 +" + result.contribution() + ")" : ""));
            });
            return;
        }
        CityStatePlayer viewer = plugin.getCityStatePlayerManager().getCityStatePlayer(event.getPlayer());
        new GuardianBeastGUI(null, city, viewer).open();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        CityState city = plugin.getCityWorldService().findCityByWorld(event.getWorld().getName());
        if (city != null) visuals.ensureVisual(city)
                .exceptionally(error -> { plugin.getGuardianBeastService().state(city).visualFailed(error); return null; });
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        visuals.worldUnloaded(event.getWorld().getName());
    }
}
