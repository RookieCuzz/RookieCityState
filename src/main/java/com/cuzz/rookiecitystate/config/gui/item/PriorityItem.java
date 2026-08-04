package com.cuzz.rookiecitystate.config.gui.item;

import com.cuzz.rookiecitystate.internal.item.ItemBuilder;

public class PriorityItem {
    private int priority;
    private ItemBuilder itemBuilder;

    public PriorityItem(int priority, ItemBuilder itemBuilder) {
        this.priority = priority;
        this.itemBuilder = itemBuilder;
    }

    public int getPriority() {
        return priority;
    }

    public ItemBuilder getItemBuilder() {
        return itemBuilder;
    }
}
