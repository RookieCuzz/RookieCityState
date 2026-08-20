package com.cuzz.rookiecitystate.world;

import com.cuzz.rookiecitystate.citystate.CityState;

public record CreationResult(boolean success, String reason, CityState cityState) {
    public static CreationResult ok(CityState cityState) { return new CreationResult(true, "", cityState); }
    public static CreationResult failed(String reason) { return new CreationResult(false, reason, null); }
}
