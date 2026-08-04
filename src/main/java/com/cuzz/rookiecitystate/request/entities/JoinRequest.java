package com.cuzz.rookiecitystate.request.entities;

import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.request.BaseRequest;
import org.jetbrains.annotations.NotNull;

public class JoinRequest extends BaseRequest<CityStatePlayer, CityState> {
    public JoinRequest() {}

    public JoinRequest(@NotNull CityStatePlayer sender, @NotNull CityState receiver) {
        super(sender, receiver);
    }

    @Override
    public Type getType() {
        return Type.JOIN;
    }

    @Override
    public boolean isValid() {
        return getSender() != null && getReceiver() != null
                && (System.currentTimeMillis() - getCreationTime()) / 1000L < MainSettings.getCityStateRequestJoinTimeout()
                && !getSender().isInCityState()
                && getReceiver().isValid()
                && getReceiver().getMemberCount() < getReceiver().getMaxMemberCount();
    }
}
