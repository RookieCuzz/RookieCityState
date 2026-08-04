package com.cuzz.rookiecitystate.config.gui;

import com.cuzz.rookiecitystate.config.gui.item.PriorityItem;
import com.cuzz.rookiecitystate.gui.BasePageableGUI;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.internal.inventory.ItemListener;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PriorityConfigGUI {
    public static class Builder extends IndexConfigGUI.Builder {
        private List<Integer> availableIndexes;
        private Map<PriorityItem, ItemListener> priorityMap = new HashMap<>();

        public Builder() {}

        @Override
        public PriorityConfigGUI.Builder fromConfig(@NotNull ConfigurationSection section, @NotNull Player papiPlayer) {
            availableIndexes(Util.getIndexes(section.getString("indexes")));
            super.fromConfig(section, papiPlayer);
            return this;
        }

        @Override
        public PriorityConfigGUI.Builder fromConfig(@NotNull ConfigurationSection section, @Nullable Player papiPlayer, @Nullable PlaceholderContainer placeholderContainer) {
            availableIndexes(Util.getIndexes(section.getString("indexes")));
            super.fromConfig(section, papiPlayer, placeholderContainer);
            return this;
        }

        @Override
        public PriorityConfigGUI.Builder pageItems(@NotNull ConfigurationSection section, @NotNull BasePageableGUI basePageableGUI) {
            availableIndexes(Util.getIndexes(section.getString("indexes")));
            super.pageItems(section, basePageableGUI);
            return this;
        }

        private Builder availableIndexes(@NotNull List<Integer> availablePositions) {
            this.availableIndexes = availablePositions;
            return this;
        }

        public Builder item(@Nullable PriorityItem priorityItem) {
            return item(priorityItem, null);
        }

        public Builder item(@Nullable PriorityItem priorityItem, @Nullable ItemListener itemListener) {
            if (priorityItem != null) {
                this.priorityMap.put(priorityItem, itemListener);
            }

            return this;
        }

        @Override
        public Inventory build() {
            Objects.requireNonNull(availableIndexes, "availableIndexes 未设置");

            if (availableIndexes.size() < priorityMap.size()) {
                throw new IllegalArgumentException("物品数量超过了可供设置的数量");
            }

            List<PriorityItem> items = new ArrayList<>(priorityMap.keySet());

            items.sort((o1, o2) -> Integer.compare(o2.getPriority(), o1.getPriority()));

            for (int i = 0; i < items.size(); i++) {
                PriorityItem item = items.get(i);

                item(availableIndexes.get(i), item.getItemBuilder().build(), priorityMap.get(item));
            }

            return super.build();
        }
    }
}
