package com.cuzz.rookiecitystate.api;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityStateManager;
import com.cuzz.rookiecitystate.player.CityStatePlayerManager;
import com.cuzz.rookiecitystate.world.CityWorldService;
import com.cuzz.rookiecitystate.wishtree.WishTreeService;
import com.cuzz.rookiecitystate.guardian.GuardianBeastService;
import com.cuzz.rookiecitystate.guardian.shop.GuardianContributionShopService;
import com.cuzz.rookiecitystate.social.CitySocialService;

public class RookieCityStateAPI {
    public static CityStateManager getCityStateManager() {
        return RookieCityState.inst().getCityStateManager();
    }

    public static CityStatePlayerManager getCityStatePlayerManager() {
        return RookieCityState.inst().getCityStatePlayerManager();
    }

    public static CityWorldService getCityWorldService() {
        return RookieCityState.inst().getCityWorldService();
    }

    public static WishTreeService getWishTreeService() {
        return RookieCityState.inst().getWishTreeService();
    }

    public static GuardianBeastService getGuardianBeastService() {
        return RookieCityState.inst().getGuardianBeastService();
    }

    public static GuardianContributionShopService getGuardianContributionShopService() {
        return RookieCityState.inst().getGuardianContributionShopService();
    }

    public static CitySocialService getCitySocialService() {
        return RookieCityState.inst().getCitySocialService();
    }
}
