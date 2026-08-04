package com.cuzz.rookiecitystate.placeholder;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.LangHelper;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.CityStateBank;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlaceholderSnapshotService {
    private final RookieCityState plugin;
    private volatile Map<UUID, Map<String, String>> snapshots = Map.of();

    public PlaceholderSnapshotService(RookieCityState plugin) {
        this.plugin = plugin;
    }

    public void refresh() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("占位符快照只能在主线程刷新");
        Bukkit.getOnlinePlayers().forEach(plugin.getCityStatePlayerManager()::getCityStatePlayer);
        Map<UUID, Map<String, String>> next = new HashMap<>();
        for (CityStatePlayer player : plugin.getCityStatePlayerManager().getLoadedCityStatePlayers()) {
            Map<String, String> values = new HashMap<>();
            CityState cityState = player.getCityState();
            values.put("is_in_city_state", String.valueOf(cityState != null));
            if (cityState != null) {
                CityStateMember member = cityState.getMember(player);
                if (member != null) {
                    values.put("name", cityState.getName());
                    values.put("member_signed_count", String.valueOf(member.getSign().getSignedCount()));
                    values.put("member_position", LangHelper.Global.getPositionName(member.getPosition()));
                    values.put("member_donated_gmoney", member.getDonated(CityStateBank.BalanceType.GMONEY).toPlainString());
                    values.put("member_join_time", LangHelper.Global.formatDateTime(member.getJoinTime()));
                    values.put("ranking", String.valueOf(plugin.getCacheCityStateManager().getRanking(cityState)));
                    values.put("owner", cityState.getOwner().getName());
                    values.put("member_count", String.valueOf(cityState.getMemberCount()));
                    values.put("max_member_count", String.valueOf(cityState.getMaxMemberCount()));
                    values.put("creation_time", LangHelper.Global.formatDateTime(cityState.getCreateTime()));
                    values.put("bank_gmoney", cityState.getCityStateBank().getBalance(CityStateBank.BalanceType.GMONEY).toPlainString());
                    values.put("online_member_count", String.valueOf(cityState.getOnlineMemberCount()));
                }
            }
            next.put(player.getUuid(), Map.copyOf(values));
        }
        snapshots = Map.copyOf(next);
    }

    public String get(UUID playerId, String key) {
        return snapshots.getOrDefault(playerId, Map.of()).get(key.toLowerCase(java.util.Locale.ROOT));
    }

    public boolean isInCityState(UUID playerId) {
        return Boolean.parseBoolean(get(playerId, "is_in_city_state"));
    }

    public void clear() { snapshots = Map.of(); }
}
