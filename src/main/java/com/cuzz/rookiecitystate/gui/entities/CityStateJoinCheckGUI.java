package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.DebugMessage;
import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.LangHelper;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.gui.BasePageableGUI;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.placeholder.PlaceholderText;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.request.Request;
import com.cuzz.rookiecitystate.request.RequestManager;
import com.cuzz.rookiecitystate.request.entities.JoinRequest;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.internal.inventory.InventoryListener;
import com.cuzz.rookiecitystate.internal.inventory.ItemListener;
import com.cuzz.rookiecitystate.internal.item.ItemBuilder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CityStateJoinCheckGUI extends BasePageableGUI {
    private final RookieCityState plugin = RookieCityState.inst();
    private final RequestManager requestManager = plugin.getRequestManager();
    private final ConfigurationSection thisGUISection = plugin.getGUIYaml("CityStateJoinCheckGUI");
    private final ConfigurationSection thisLangSection = plugin.getLangYaml().getConfigurationSection("CityStateJoinCheckGUI");
    private final Player bukkitPlayer = getBukkitPlayer();
    private List<Integer> itemIndexes; // 请求物品位置
    private int itemIndexCount; // 请求物品位置数量
    private final CityStateMember cityStateMember;
    private final CityState cityState;

    private List<Request> requests;
    private int requestCount;

    public CityStateJoinCheckGUI(@Nullable GUI lastGUI, @NotNull CityStateMember cityStateMember) {
        super(lastGUI, Type.PLAYER_JOIN_CHECK, cityStateMember.getCityStatePlayer());

        this.cityStateMember = cityStateMember;
        this.cityState = cityStateMember.getCityState();

        PluginLogger.debug("开始: 加载 'items.request.indexes'.");
        this.itemIndexes = Util.getIndexes(thisGUISection.getString("items.request.indexes"));
        PluginLogger.debug("结束: 加载 'items.request.indexes'.");

        this.itemIndexCount = itemIndexes.size();
    }

    @Override
    public void update() {
        this.requests = cityState.getReceivedRequests().stream()
                .filter(request -> request.getType() == Request.Type.JOIN && request.isValid())
                .collect(Collectors.toList());
        this.requestCount = requests.size();

        setPageCount(requestCount % itemIndexCount == 0 ? requestCount / itemIndexCount : requestCount / itemIndexCount + 1);
    }

    @Override
    public Inventory createInventory() {
        Map<Integer, Request> indexMap = new HashMap<>();
        IndexConfigGUI.Builder guiBuilder = new IndexConfigGUI.Builder();

        guiBuilder.listener(new InventoryListener() {
            @Override
            public void onClick(InventoryClickEvent event) {
                int index = event.getRawSlot();

                if (indexMap.containsKey(index)) {
                    Request request = indexMap.get(index);
                    CityStatePlayer sender = (CityStatePlayer) request.getSender();
                    InventoryAction action = event.getAction();

                    if (!cityStateMember.isValid() || !cityStateMember.hasPermission(com.cuzz.rookiecitystate.citystate.member.CityStatePermission.PLAYER_JOIN_CHECK)) {
                        Util.sendMsg(bukkitPlayer, "&c你没有审批入会申请的权限。");
                        close();
                        return;
                    }
                    if (requestManager.getRequest(request.getUuid()) != request || !request.isValid()
                            || sender.isInCityState() || cityState.getMemberCount() >= cityState.getMaxMemberCount()) {
                        Util.sendMsg(bukkitPlayer, thisLangSection.getString("invalid"));
                        request.delete();
                        reopen(20L);
                        return;
                    }

                    if (action == InventoryAction.PICKUP_ALL) {
                        try {
                            cityState.addMember(sender);
                        } catch (RuntimeException exception) {
                            Util.sendMsg(bukkitPlayer, "&c批准失败: " + exception.getMessage());
                            reopen(20L);
                            return;
                        }

                        cityState.broadcastMessage(PlaceholderText.replacePlaceholders(thisLangSection.getString("accept.broadcast"), new PlaceholderContainer()
                                .add("player", sender.getName())));
                        reopen(20L);
                        return;
                    }

                    if (action == InventoryAction.PICKUP_HALF) {
                        requestManager.deleteRequest(request);
                        Util.sendMsg(bukkitPlayer, PlaceholderText.replacePlaceholders(thisLangSection.getString("deny.approver"), new PlaceholderContainer()
                                .add("player", sender.getName())));
                        reopen(20L);
                    }
                }
            }
        });

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_BASIC);
        guiBuilder.fromConfig(thisGUISection, bukkitPlayer, new PlaceholderContainer()
                        .add("page", getCurrentPage() + 1)
                        .add("total_page", getPageCount()));
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_BASIC);

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.page_items");
        guiBuilder.pageItems(thisGUISection.getConfigurationSection("items.page_items"), this);
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.page_items");

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.back");
        guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.back"), bukkitPlayer), new ItemListener() {
                    @Override
                    public void onClick(InventoryClickEvent event) {
                        if (canBack()) {
                            back();
                        }
                    }
                });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.back");

        if (getPageCount() > 0) {
            int requestCounter = getCurrentPage() * itemIndexes.size();
            int loopCount = requestCount - requestCounter < itemIndexCount ? requestCount - requestCounter : itemIndexCount; // 循环次数，根据当前能够显示的数量决定

            for (int i = 0; i < loopCount; i++) {
                JoinRequest request = (JoinRequest) requests.get(requestCounter++);
                CityStatePlayer sender = request.getSender();

                PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.request.icon");
                ItemBuilder itemBuilder = GUIItemManager.getItemBuilder(thisGUISection.getConfigurationSection("items.request.icon"), sender.getOfflineBukkitPlayer(), new PlaceholderContainer()
                        .add("sender_name", sender.getName())
                        .add("send_time", LangHelper.Global.formatDateTime(request.getCreationTime())));
                PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.request.icon");

                guiBuilder.item(itemIndexes.get(i), itemBuilder.build());
                indexMap.put(itemIndexes.get(i), request);
            }
        }

        return guiBuilder.build();
    }

    @Override
    public boolean canUse() {
        return cityStateMember.isValid()
                && cityStateMember.hasPermission(com.cuzz.rookiecitystate.citystate.member.CityStatePermission.PLAYER_JOIN_CHECK);
    }
}
