package com.cuzz.rookiecitystate.citystate.member;

import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CityStateOwner extends CityStateMember {
    public CityStateOwner(@NotNull CityState cityState, @NotNull CityStatePlayer cityStatePlayer) {
        super(cityState, cityStatePlayer);
    }

    @Override
    public CityStatePosition getPosition() {
        return CityStatePosition.OWNER;
    }

    @Override
    public void setPermission(@NotNull CityStatePermission cityStatePermission, boolean b) {
        throw new RuntimeException("会长不允许被设置权限");
    }

    @Override
    public Set<CityStatePermission> getPermissions() {
        return new HashSet<>(Arrays.asList(CityStatePermission.values()));
    }
}
