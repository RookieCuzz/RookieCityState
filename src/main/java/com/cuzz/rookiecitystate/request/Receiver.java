package com.cuzz.rookiecitystate.request;

import com.cuzz.rookiecitystate.RookieCityState;

import java.util.List;

public interface Receiver {
    enum Type {
        CITY_STATE, CITY_STATE_PLAYER, CITY_STATE_MEMBER
    }

    default List<Request> getReceivedRequests() {
        return RookieCityState.inst().getRequestManager().getReceivedRequests(this);
    }
}
