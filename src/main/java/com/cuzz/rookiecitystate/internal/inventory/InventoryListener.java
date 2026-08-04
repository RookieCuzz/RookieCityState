package com.cuzz.rookiecitystate.internal.inventory;

import org.bukkit.event.inventory.InventoryClickEvent;

@FunctionalInterface
public interface InventoryListener {
    void onClick(InventoryClickEvent event);
}
