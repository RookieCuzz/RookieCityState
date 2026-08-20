package com.cuzz.rookiecitystate.api.event;

import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.guardian.shop.GuardianPurchaseResult;
import com.cuzz.rookiecitystate.guardian.shop.GuardianShopProduct;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class GuardianShopPurchasedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CityState cityState;
    private final CityStatePlayer player;
    private final GuardianShopProduct product;
    private final GuardianPurchaseResult result;

    public GuardianShopPurchasedEvent(CityState cityState, CityStatePlayer player,
                                      GuardianShopProduct product, GuardianPurchaseResult result) {
        this.cityState = cityState; this.player = player; this.product = product; this.result = result;
    }
    public CityState getCityState() { return cityState; }
    public CityStatePlayer getPlayer() { return player; }
    public GuardianShopProduct getProduct() { return product; }
    public GuardianPurchaseResult getResult() { return result; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
