package com.cuzz.rookiecitystate.gui;

import com.cuzz.rookiecitystate.DebugMessage;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.internal.inventory.ItemListener;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseConfirmGUI extends BasePlayerGUI {
    private final ConfigurationSection section;
    private final Player bukkitPlayer = getBukkitPlayer();
    private final PlaceholderContainer confirmPlaceholderContainer;

    protected BaseConfirmGUI(@Nullable GUI lastGUI, @NotNull CityStatePlayer cityStatePlayer, @NotNull ConfigurationSection section) {
        this(lastGUI, cityStatePlayer, section, null);
    }

    protected BaseConfirmGUI(@Nullable GUI lastGUI, @NotNull CityStatePlayer cityStatePlayer, @NotNull ConfigurationSection section, @Nullable PlaceholderContainer confirmPlaceholderContainer) {
        super(lastGUI, Type.CONFIRM, cityStatePlayer);

        this.section = section;
        this.confirmPlaceholderContainer = confirmPlaceholderContainer;
    }

    public PlaceholderContainer getConfirmPlaceholderContainer() {
        return confirmPlaceholderContainer;
    }

    @Override
    public abstract boolean canUse();

    public abstract void onConfirm();

    public abstract void onCancel();

    @Override
    public Inventory createInventory() {
        IndexConfigGUI.Builder guiBuilder = new IndexConfigGUI.Builder();

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_BASIC);
        guiBuilder.fromConfig(section, confirmPlaceholderContainer);
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_BASIC);

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.cancel");
        guiBuilder.item(GUIItemManager.getIndexItem(section.getConfigurationSection("items.cancel"), bukkitPlayer), new ItemListener() {
                    @Override
                    public void onClick(InventoryClickEvent event) {
                        onCancel();
                    }
                });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.cancel");

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.confirm");
        guiBuilder.item(GUIItemManager.getIndexItem(section.getConfigurationSection("items.confirm"), bukkitPlayer, confirmPlaceholderContainer), new ItemListener() {
            @Override
            public void onClick(InventoryClickEvent event) {
                onConfirm();
            }
        });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.confirm");

        return guiBuilder.build();
    }
}
