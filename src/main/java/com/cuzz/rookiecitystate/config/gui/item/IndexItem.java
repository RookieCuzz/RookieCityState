package com.cuzz.rookiecitystate.config.gui.item;

import com.cuzz.rookiecitystate.internal.item.ItemBuilder;

public class IndexItem {
    private int index;
    private ItemBuilder itemBuilder;

    public IndexItem(int index, ItemBuilder itemBuilder) {
        this.index = index;
        this.itemBuilder = itemBuilder;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void setItemBuilder(ItemBuilder itemBuilder) {
        this.itemBuilder = itemBuilder;
    }

    public ItemBuilder getItemBuilder() {
        return itemBuilder;
    }

    public int getIndex() {
        return index;
    }
}
