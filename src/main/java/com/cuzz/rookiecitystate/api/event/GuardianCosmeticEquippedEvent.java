package com.cuzz.rookiecitystate.api.event;

import com.cuzz.rookiecitystate.guardian.shop.GuardianCosmeticSlot;
import com.cuzz.rookiecitystate.guardian.shop.GuardianShopProduct;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GuardianCosmeticEquippedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CityStatePlayer player;
    private final GuardianCosmeticSlot slot;
    private final GuardianShopProduct product;

    public GuardianCosmeticEquippedEvent(CityStatePlayer player, GuardianCosmeticSlot slot,
                                         @Nullable GuardianShopProduct product) {
        this.player = player; this.slot = slot; this.product = product;
    }
    public CityStatePlayer getPlayer() { return player; }
    public GuardianCosmeticSlot getSlot() { return slot; }
    public @Nullable GuardianShopProduct getProduct() { return product; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
