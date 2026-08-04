package com.cuzz.rookiecitystate.internal.inventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public class InventoryBuilder {
    private final Map<Integer, ItemStack> items = new LinkedHashMap<>();
    private final Map<Integer, ItemListener> listeners = new LinkedHashMap<>();
    private String title = "";
    private int row = 1;
    private boolean colored;
    private InventoryListener inventoryListener;

    public InventoryBuilder title(String title) { this.title = title == null ? "" : title; return this; }
    public InventoryBuilder row(int row) { this.row = row; return this; }
    public InventoryBuilder colored() { this.colored = true; return this; }
    public InventoryBuilder colored(boolean colored) { this.colored = colored; return this; }
    public InventoryBuilder listener(InventoryListener listener) { this.inventoryListener = listener; return this; }

    public InventoryBuilder item(int slot, ItemStack item) {
        return item(slot, item, null);
    }

    public InventoryBuilder item(int slot, ItemStack item, ItemListener listener) {
        if (item == null) return this;
        items.put(slot, item.clone());
        if (listener != null) listeners.put(slot, listener);
        return this;
    }

    public Inventory build() {
        if (row < 1 || row > 6) throw new IllegalArgumentException("GUI 行数必须为 1-6: " + row);
        int size = row * 9;
        GuiHolder holder = new GuiHolder();
        Component component = colored
                ? LegacyComponentSerializer.legacyAmpersand().deserialize(title.replace('§', '&'))
                : Component.text(title);
        Inventory inventory = Bukkit.createInventory(holder, size, component);
        holder.inventory(inventory);
        holder.inventoryListener(inventoryListener);
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= size) throw new IllegalArgumentException("GUI 槽位越界: " + (slot + 1));
            inventory.setItem(slot, entry.getValue());
            holder.itemListener(slot, listeners.get(slot));
        }
        return inventory;
    }
}
