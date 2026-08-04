package com.cuzz.rookiecitystate.request;

import com.cuzz.rookiecitystate.RookieCityState;

import java.util.List;

public interface Sender {
    enum Type {
        CITY_STATE, CITY_STATE_PLAYER, CITY_STATE_MEMBER
    }

    default List<Request> getSentRequests() {
        return RookieCityState.inst().getRequestManager().getSentRequests(this);
    }
}
