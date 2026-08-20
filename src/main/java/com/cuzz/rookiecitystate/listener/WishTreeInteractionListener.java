package com.cuzz.rookiecitystate.listener;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.gui.entities.WishTreeGUI;
import com.cuzz.rookiecitystate.util.Util;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/** Opens the member-only wish tree menu from the physical tree interaction entity. */
public final class WishTreeInteractionListener implements Listener {
    private final RookieCityState plugin;
    private final NamespacedKey cityKey;

    public WishTreeInteractionListener(RookieCityState plugin) {
        this.plugin = plugin;
        this.cityKey = new NamespacedKey(plugin, "wish_tree_city");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;
        String rawCityId = interaction.getPersistentDataContainer().get(cityKey, PersistentDataType.STRING);
        if (rawCityId == null) return;
        event.setCancelled(true);

        CityState cityState;
        try {
            cityState = plugin.getCityStateManager().getCityState(UUID.fromString(rawCityId));
        } catch (IllegalArgumentException ignored) {
            return;
        }
        open(event.getPlayer(), cityState);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !insideWishTree(block)) return;
        CityState cityState = plugin.getCityWorldService().findCityByWorld(block.getWorld().getName());
        if (cityState == null) return;
        event.setCancelled(true);
        open(event.getPlayer(), cityState);
    }

    private boolean insideWishTree(Block block) {
        return block.getX() >= MainSettings.getWishTreeOriginX() - 7
                && block.getX() < MainSettings.getWishTreeOriginX() + 8
                && block.getY() >= MainSettings.getWishTreeOriginY()
                && block.getY() < MainSettings.getWishTreeOriginY() + 24
                && block.getZ() >= MainSettings.getWishTreeOriginZ() - 7
                && block.getZ() < MainSettings.getWishTreeOriginZ() + 8;
    }

    private void open(org.bukkit.entity.Player player, CityState cityState) {
        if (cityState == null || !cityState.isWorldReady()) {
            Util.sendMsg(player, "&c许愿树暂不可用。");
            return;
        }
        if (!cityState.getWorldName().equals(player.getWorld().getName())) return;
        CityStateMember member = cityState.getMember(player.getUniqueId());
        if (member == null) {
            Util.sendMsg(player, "&e访客可以观看许愿树，但不能进行互动。");
            return;
        }
        new WishTreeGUI(null, member).open();
    }
}
