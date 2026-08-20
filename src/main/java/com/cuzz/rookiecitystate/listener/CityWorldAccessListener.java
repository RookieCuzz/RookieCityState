package com.cuzz.rookiecitystate.listener;

import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.world.CityWorldService;
import com.cuzz.rookiecitystate.world.CityWorldState;
import com.cuzz.rookiecitystate.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CityWorldAccessListener implements Listener {
    private static final String[] PROTECTION_BYPASSES = {
            "rookieregions.bypass.build", "rookieregions.bypass.block-break",
            "rookieregions.bypass.block-place", "rookieregions.bypass.use",
            "rookieregions.bypass.container", "rookieregions.bypass.pvp",
            "rookieregions.bypass.entry", "rookieregions.bypass.explosion"
    };
    private final CityWorldService worlds;
    private final Map<UUID, PermissionAttachment> bypassAttachments = new HashMap<>();

    public CityWorldAccessListener(CityWorldService worlds) {
        this.worlds = worlds;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;
        String targetName = to.getWorld().getName();
        if (!worlds.isManagedWorld(targetName)) return;
        Player player = event.getPlayer();
        if (worlds.isRecoveryWorld(targetName) || worlds.isTemplateWorld(targetName)) {
            if (!player.hasPermission("rookiecitystate.admin")) deny(event, player);
            else {
                grantProtectionBypass(player);
                if (!worlds.isManagedWorld(event.getFrom().getWorld().getName())) worlds.rememberReturnIfRequired(player);
            }
            return;
        }
        CityState cityState = worlds.findCityByWorld(targetName);
        if (cityState == null || cityState.getWorldState() != CityWorldState.READY || !worlds.canAccess(player, cityState)) {
            deny(event, player);
            return;
        }
        if (player.hasPermission("rookiecitystate.admin")) grantProtectionBypass(player);
        if (!worlds.isManagedWorld(event.getFrom().getWorld().getName())) worlds.rememberReturnIfRequired(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleportTerminal(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(com.cuzz.rookiecitystate.RookieCityState.inst(), () -> {
            if (!player.isOnline() || !player.hasPermission("rookiecitystate.admin")
                    || !worlds.isManagedWorld(player.getWorld().getName())) {
                removeProtectionBypass(player);
            } else {
                grantProtectionBypass(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        worlds.onWorldLeft(event.getFrom().getName());
        worlds.onWorldEntered(event.getPlayer().getWorld().getName());
        if (worlds.isManagedWorld(event.getPlayer().getWorld().getName())) {
            if (event.getPlayer().hasPermission("rookiecitystate.admin")) {
                grantProtectionBypass(event.getPlayer());
            } else {
                removeProtectionBypass(event.getPlayer());
            }
        } else {
            removeProtectionBypass(event.getPlayer());
            com.cuzz.rookiecitystate.RookieCityState.inst().getCityStatePlayerManager()
                    .getCityStatePlayer(event.getPlayer()).clearWorldReturnLocation();
        }
        verifyPresentWorld(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(com.cuzz.rookiecitystate.RookieCityState.inst(),
                () -> {
                    if (!worlds.isManagedWorld(event.getPlayer().getWorld().getName())) {
                        com.cuzz.rookiecitystate.RookieCityState.inst().getCityStatePlayerManager()
                                .getCityStatePlayer(event.getPlayer()).clearWorldReturnLocation();
                    }
                    verifyPresentWorld(event.getPlayer());
                });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        String worldName = event.getPlayer().getWorld().getName();
        removeProtectionBypass(event.getPlayer());
        Bukkit.getScheduler().runTask(com.cuzz.rookiecitystate.RookieCityState.inst(),
                () -> worlds.onWorldLeft(worldName));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (worlds.isManagedWorld(event.getFrom().getWorld().getName())
                || (event.getTo() != null && event.getTo().getWorld() != null
                && worlds.isManagedWorld(event.getTo().getWorld().getName()))) {
            event.setCancelled(true);
            Util.sendMsg(event.getPlayer(), "&c城邦世界禁用传送门，请使用城邦入口或 /cs world exit。");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        CityState cityState = worlds.findCityByWorld(player.getWorld().getName());
        if (cityState != null && worlds.canAccess(player, cityState) && cityState.hasSpawn()
                && cityState.getSpawn().getLocation() != null) {
            event.setRespawnLocation(cityState.getSpawn().getLocation());
        } else if (worlds.isManagedWorld(player.getWorld().getName()) && worlds.getFallbackLocation() != null) {
            event.setRespawnLocation(worlds.getFallbackLocation());
        }
    }

    private void verifyPresentWorld(Player player) {
        String worldName = player.getWorld().getName();
        if (!worlds.isManagedWorld(worldName)) return;
        if (worlds.isRecoveryWorld(worldName) || worlds.isTemplateWorld(worldName)) {
            if (!player.hasPermission("rookiecitystate.admin")) worlds.exit(player);
            else grantProtectionBypass(player);
            return;
        }
        CityState cityState = worlds.findCityByWorld(worldName);
        if (cityState == null || !worlds.canAccess(player, cityState)) worlds.exit(player);
    }

    private void deny(PlayerTeleportEvent event, Player player) {
        event.setCancelled(true);
        Util.sendMsg(player, "&c你无权进入该城邦世界。");
    }

    private void grantProtectionBypass(Player player) {
        removeProtectionBypass(player);
        PermissionAttachment attachment = player.addAttachment(com.cuzz.rookiecitystate.RookieCityState.inst());
        for (String permission : PROTECTION_BYPASSES) attachment.setPermission(permission, true);
        bypassAttachments.put(player.getUniqueId(), attachment);
        player.recalculatePermissions();
    }

    private void removeProtectionBypass(Player player) {
        PermissionAttachment attachment = bypassAttachments.remove(player.getUniqueId());
        if (attachment != null) player.removeAttachment(attachment);
    }
}
