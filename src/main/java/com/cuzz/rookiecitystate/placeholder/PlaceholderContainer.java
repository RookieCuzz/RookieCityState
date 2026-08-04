package com.cuzz.rookiecitystate.placeholder;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.LangHelper;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.CityStateBank;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.internal.util.TimeUtil;
import com.cuzz.rookiecitystate.internal.text.TextService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于内部的占位符
 */
public class PlaceholderContainer {
    private Map<String, Placeholder> placeholderMap = new HashMap<>();

    public boolean hasPlaceholder(@NotNull String key) {
        return placeholderMap.containsKey(key);
    }

    public Placeholder getPlaceholder(@NotNull String key) {
        return placeholderMap.get(key);
    }

    public List<Placeholder> getPlaceholders() {
        return new ArrayList<>(placeholderMap.values());
    }

    /**
     * 添加定义好的城邦占位符
     * @param cityState
     * @return
     */
    public PlaceholderContainer addCityStatePlaceholders(@NotNull CityState cityState) {
        add("city_state_name", cityState.getName());
        add("city_state_ranking", RookieCityState.inst().getCacheCityStateManager().getRanking(cityState));
        add("city_state_owner", cityState.getOwner().getName());
        add("city_state_gmoney", TextService.formatDecimal(cityState.getCityStateBank().getBalance(CityStateBank.BalanceType.GMONEY)));
        add("city_state_online_member_count", cityState.getOnlineMemberCount());
        add("city_state_member_count", cityState.getMemberCount());
        add("city_state_max_member_count", cityState.getMaxMemberCount());
        add("city_state_creation_time", TextService.formatDate(cityState.getCreateTime()));
        return this;
    }

    public PlaceholderContainer addCityStateMemberPlaceholders(@NotNull CityStateMember cityStateMember) {
        add("member_is_signed_today", cityStateMember.getSign().isSignedToday());
        add("member_signed_count", cityStateMember.getSign().getSignedCount());
        add("member_name", cityStateMember.getName());
        add("member_position", LangHelper.Global.getPositionName(cityStateMember.getPosition()));
        add("member_join_time", LangHelper.Global.formatDateTime(cityStateMember.getJoinTime()));
        add("member_donated_gmoney", TextService.formatDecimal(cityStateMember.getDonated(CityStateBank.BalanceType.GMONEY)));
        return this;
    }

    public PlaceholderContainer add(@NotNull String key, @NotNull Object value) {
        placeholderMap.put(key, new Placeholder(key, value));
        return this;
    }
}
