package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.DebugMessage;
import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.gui.BasePageableGUI;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.CityStateIcon;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.citystate.member.CityStatePermission;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.internal.inventory.InventoryListener;
import com.cuzz.rookiecitystate.internal.inventory.ItemListener;
import com.cuzz.rookiecitystate.internal.item.ItemBuilder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CityStateIconRepositoryGUI extends BasePageableGUI {
    private RookieCityState plugin = RookieCityState.inst();
    private final ConfigurationSection thisGUISection = plugin.getGUIYaml("CityStateIconRepositoryGUI");
    private final List<Integer> itemIndexes; // 得到所有可供城邦设置的位置
    private int itemIndexCount;
    private Player bukkitPlayer = getBukkitPlayer();
    private CityStateMember cityStateMember;
    private List<CityStateIcon> icons = new ArrayList<>();
    private int iconCount;
    private CityState cityState;

    public CityStateIconRepositoryGUI(@Nullable GUI lastGUI, CityStateMember cityStateMember) {
        super(lastGUI, Type.ICON_REPOSITORY, cityStateMember.getCityStatePlayer());

        this.cityStateMember = cityStateMember;
        this.cityState = cityStateMember.getCityState();

        PluginLogger.debug("开始: 加载 'items.city_state_icon.indexes'.");
        this.itemIndexes = Util.getIndexes(thisGUISection.getString("items.city_state_icon.indexes"));
        PluginLogger.debug("结束: 加载 'items.city_state_icon.indexes'.");
        this.itemIndexCount = itemIndexes.size();
    }

    @Override
    public void update() {
        this.icons = cityState.getIcons();

        icons.add(0, null);
        CityStateIcon current = cityState.getCurrentIcon();
        icons.sort(Comparator
                .comparingInt((CityStateIcon icon) -> icon == null ? 0 : icon == current ? 1 : 2)
                .thenComparing(icon -> icon == null ? "" : icon.getUuid().toString()));

        this.iconCount = icons.size();
        setPageCount(iconCount % itemIndexCount == 0 ? iconCount / itemIndexCount : iconCount / itemIndexCount + 1);
    }

    @Override
    public boolean canUse() {
        return cityStateMember.isValid() && cityStateMember.hasPermission(CityStatePermission.USE_ICON_REPOSITORY);
    }

    @Override
    public Inventory createInventory() {
        Map<Integer, CityStateIcon> indexMap = new HashMap<>();
        IndexConfigGUI.Builder guiBuilder = new IndexConfigGUI.Builder();

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_BASIC);
        guiBuilder.fromConfig(thisGUISection, bukkitPlayer, new PlaceholderContainer()
                .add("total_page", getPageCount())
                .add("page", getCurrentPage() + 1));
        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_BASIC);


        guiBuilder.listener(new InventoryListener() {
                    @Override
                    public void onClick(InventoryClickEvent event) {
                        int slot = event.getRawSlot();

                        if (!indexMap.containsKey(slot)) {
                            return;
                        }

                        CityStateIcon cityStateIcon = indexMap.get(slot);

                        if (cityStateIcon == null) {
                            cityState.setCurrentIcon(null);
                        } else {
                            if (!cityStateIcon.isValid()) {
                                reopen();
                                return;
                            }

                            cityState.setCurrentIcon(cityStateIcon);
                        }

                        reopen();
                    }
                });


        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.back");
        guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.back"), bukkitPlayer), new ItemListener() {
            @Override
            public void onClick(InventoryClickEvent event) {
                back();
            }
        });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.back");

        if (getPageCount() > 0) {
            int itemCounter = getCurrentPage() * itemIndexCount;
            int loopCount = iconCount - itemCounter < itemIndexCount ? iconCount - itemCounter : itemIndexCount; // 循环次数，根据当前能够显示的数量决定

            for (int i = 0; i < loopCount; i++) {
                CityStateIcon cityStateIcon = icons.get(itemCounter++);
                String path = "items.city_state_icon." + (Objects.equals(cityStateIcon, cityState.getCurrentIcon()) ? "using" : "not_using") + ".icon";

                PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, path);
                ItemBuilder itemBuilder = GUIItemManager.getItemBuilder(thisGUISection.getConfigurationSection(path)
                        , bukkitPlayer);
                PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, path);

                if (cityStateIcon == null) {
                    itemBuilder.material(MainSettings.getCityStateIconDefaultMaterial());

                    if (MainSettings.getCityStateIconDefaultFirstLore() != null && !MainSettings.getCityStateIconDefaultFirstLore().isEmpty()) {
                        itemBuilder.insertLore(0, MainSettings.getCityStateIconDefaultFirstLore());
                    }
                } else {
                    itemBuilder.material(cityStateIcon.getMaterial());

                    if (cityStateIcon.getFirstLore() != null) {
                        itemBuilder.insertLore(0, cityStateIcon.getFirstLore());
                    }
                }

                if (cityStateIcon != null) {
                    itemBuilder.displayName(cityStateIcon.getDisplayName());
                }

                guiBuilder.item(itemIndexes.get(i), itemBuilder.build());
                indexMap.put(itemIndexes.get(i), cityStateIcon);
            }
        }

        return guiBuilder.build();
    }
}
