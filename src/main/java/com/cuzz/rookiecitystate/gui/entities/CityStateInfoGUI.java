package com.cuzz.rookiecitystate.gui.entities;

import com.cuzz.rookiecitystate.DebugMessage;
import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.config.gui.IndexConfigGUI;
import com.cuzz.rookiecitystate.config.gui.item.GUIItemManager;
import com.cuzz.rookiecitystate.gui.BasePlayerGUI;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.citystate.member.CityStatePermission;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.request.entities.JoinRequest;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.internal.inventory.ItemListener;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Collectors;
import com.cuzz.rookiecitystate.world.CityWorldState;
import com.cuzz.rookiecitystate.world.WorldVisibility;
import com.cuzz.rookiecitystate.social.CitySocialView;


/**
 * 查看城邦成员，申请加入城邦
 */
public class CityStateInfoGUI extends BasePlayerGUI {
    private final Player bukkitPlayer;
    private final CityState cityState;
    private final RookieCityState plugin = RookieCityState.inst();
    private final ConfigurationSection thisGUISection = plugin.getGUIYaml("CityStateInfoGUI");
    private final ConfigurationSection thisLangSection = plugin.getLangYaml().getConfigurationSection("CityStateInfoGUI");

    public CityStateInfoGUI(@Nullable GUI lastGUI, @NotNull CityStatePlayer cityStatePlayer, @NotNull CityState cityState) {
        super(lastGUI, Type.INFO, cityStatePlayer);

        this.bukkitPlayer = cityStatePlayer.getBukkitPlayer();
        this.cityState = cityState;
    }

    @Override
    public Inventory createInventory() {
        IndexConfigGUI.Builder guiBuilder = new IndexConfigGUI.Builder();

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_BASIC);
        guiBuilder.fromConfig(thisGUISection, bukkitPlayer, new PlaceholderContainer().addCityStatePlaceholders(cityState));
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_BASIC);

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.request_join");
        guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.request_join"), bukkitPlayer, new PlaceholderContainer().addCityStatePlaceholders(cityState)), new ItemListener() {
            @Override
            public void onClick(InventoryClickEvent event) {
                close();

                if (cityStatePlayer.isInCityState()) {
                    Util.sendMsg(bukkitPlayer, "&c你已经加入城邦，不能再发送申请。");
                    return;
                }
                if (!cityState.isValid()) {
                    Util.sendMsg(bukkitPlayer, "&c该城邦已经失效。");
                    return;
                }
                if (cityState.getMemberCount() >= cityState.getMaxMemberCount()) {
                    Util.sendMsg(bukkitPlayer, thisLangSection.getString("request_join.city_state_full"));
                    return;
                }

                for (JoinRequest joinRequest : cityStatePlayer.getSentRequests().stream().filter(request -> request instanceof JoinRequest).map(request -> (JoinRequest) request).collect(Collectors.toList())) {
                    if (joinRequest.getReceiver().equals(cityState)) {
                        Util.sendMsg(bukkitPlayer, thisLangSection.getString("request_join.already_have"));
                        return;
                    }
                }

                try {
                    new JoinRequest(cityStatePlayer, cityState).send();
                } catch (RuntimeException exception) {
                    Util.sendMsg(bukkitPlayer, "&c申请发送失败: " + exception.getMessage());
                    return;
                }

                cityState.getMembers().stream().filter(cityStateMember -> cityStateMember.hasPermission(CityStatePermission.PLAYER_JOIN_CHECK)).filter(CityStateMember::isOnline).forEach(cityStateMember -> {
                    Util.sendMsg(cityStateMember.getCityStatePlayer().getBukkitPlayer(), thisLangSection.getString("request_join.received"), new PlaceholderContainer().add("sender", bukkitPlayer.getName()));
                });

                Util.sendMsg(bukkitPlayer, thisLangSection.getString("request_join.success"));
                    }
                });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.request_join");

        if (cityState.getWorldVisibility() == WorldVisibility.PUBLIC
                && cityState.getWorldState() == CityWorldState.READY && thisGUISection.contains("items.visit")) {
            guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.visit"), bukkitPlayer,
                    new PlaceholderContainer().addCityStatePlaceholders(cityState)), event -> {
                close();
                plugin.getCityWorldService().enter(bukkitPlayer, cityState).thenAccept(result ->
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> Util.sendMsg(bukkitPlayer,
                                result.success() ? "&a已进入城邦参观。" : "&c无法参观: " + result.reason())));
            });
        }

        if (thisGUISection.contains("items.social_like")) {
            CitySocialView social = plugin.getCitySocialService().getView(bukkitPlayer, cityState);
            String state;
            if (social.status() == CitySocialView.Status.ERROR) state = "error";
            else if (cityState.getMember(bukkitPlayer.getUniqueId()) != null) state = "member";
            else if (cityState.getWorldVisibility() != WorldVisibility.PUBLIC
                    || cityState.getWorldState() != CityWorldState.READY) state = "unavailable";
            else if (social.likedThisWeek()) state = "liked";
            else if (social.votesRemaining() <= 0) state = "no_votes";
            else if (social.qualified()) state = "qualified";
            else state = "locked";
            PlaceholderContainer socialValues = new PlaceholderContainer().addCityStatePlaceholders(cityState)
                    .add("social_total_likes", social.totalLikes())
                    .add("social_7d_visitors", social.recentVisitors())
                    .add("social_7d_likes", social.recentLikes())
                    .add("social_hot_score", social.hotScore())
                    .add("social_hot_rank", social.hotRank())
                    .add("social_qualification_seconds", plugin.getCitySocialService().getConfig().qualificationSeconds())
                    .add("social_votes_remaining", social.votesRemaining());
            guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.social_like." + state),
                    bukkitPlayer, socialValues), state.equals("qualified") ? event -> {
                close(); new CityLikeConfirmGUI(CityStateInfoGUI.this, cityStatePlayer, cityState).open();
            } : null);
        }

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.members");
        guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.members"), bukkitPlayer, new PlaceholderContainer().addCityStatePlaceholders(cityState)), new ItemListener() {
                    @Override
                    public void onClick(InventoryClickEvent event) {
                        close();
                        new CityStateMemberListGUI(CityStateInfoGUI.this, cityState, cityStatePlayer).open();
                    }
                });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.members");

        PluginLogger.debug(DebugMessage.BEGIN_GUI_LOAD_ITEM, "items.back");
        guiBuilder.item(GUIItemManager.getIndexItem(thisGUISection.getConfigurationSection("items.back"), bukkitPlayer, new PlaceholderContainer().addCityStatePlaceholders(cityState)), new ItemListener() {
                    @Override
                    public void onClick(InventoryClickEvent event) {
                        if (canBack()) {
                            back();
                        }
                    }
                });
        PluginLogger.debug(DebugMessage.END_GUI_LOAD_ITEM, "items.back");

        return guiBuilder.build();
    }

    public CityState getCityState() {
        return cityState;
    }

    @Override
    public boolean canUse() {
        return cityState.isValid();
    }
}
