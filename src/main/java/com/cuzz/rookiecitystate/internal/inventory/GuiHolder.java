package com.cuzz.rookiecitystate.internal.inventory;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class GuiHolder implements InventoryHolder {
    private final Map<Integer, ItemListener> itemListeners = new HashMap<>();
    private InventoryListener inventoryListener;
    private Inventory inventory;

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    void itemListener(int slot, ItemListener listener) {
        if (listener != null) itemListeners.put(slot, listener);
    }

    void inventoryListener(InventoryListener listener) {
        this.inventoryListener = listener;
    }

    public void dispatch(InventoryClickEvent event) {
        if (event.getRawSlot() >= 0 && event.getRawSlot() < inventory.getSize()) {
            ItemListener itemListener = itemListeners.get(event.getRawSlot());
            if (itemListener != null) itemListener.onClick(event);
            if (inventoryListener != null) inventoryListener.onClick(event);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
