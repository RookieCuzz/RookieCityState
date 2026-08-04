package com.cuzz.rookiecitystate.api;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityStateManager;
import com.cuzz.rookiecitystate.player.CityStatePlayerManager;

public class RookieCityStateAPI {
    public static CityStateManager getCityStateManager() {
        return RookieCityState.inst().getCityStateManager();
    }

    public static CityStatePlayerManager getCityStatePlayerManager() {
        return RookieCityState.inst().getCityStatePlayerManager();
    }
}
