package com.cuzz.rookiecitystate.gui;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.CityStateBank;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.citystate.member.CityStatePermission;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.transaction.TransactionService;
import com.cuzz.rookiecitystate.util.Util;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public abstract class ShopItemConfirmGUI extends BaseConfirmGUI {
    private final CityState cityState;
    private final CityStateMember cityStateMember;
    private final BigDecimal price;

    public ShopItemConfirmGUI(@NotNull GUI lastGUI, @NotNull CityStateMember cityStateMember, @NotNull ConfigurationSection section, @NotNull PlaceholderContainer placeholderContainer, double price) {
        super(lastGUI, cityStateMember.getCityStatePlayer(), section, placeholderContainer);

        this.cityState = cityStateMember.getCityState();
        this.cityStateMember = cityStateMember;
        this.price = BigDecimal.valueOf(price);
    }

    public CityState getCityState() {
        return cityState;
    }

    @Override
    public boolean canUse() {
        return cityStateMember.isValid()
                && cityStateMember.hasPermission(CityStatePermission.USE_SHOP)
                && price.signum() >= 0
                && cityState.getCityStateBank().has(CityStateBank.BalanceType.GMONEY, price);
    }

    @Override
    public void onConfirm() {
        TransactionService.Result result = RookieCityState.inst().getTransactionService().execute(
                "城邦商店: " + cityState.getName(),
                () -> {
                    if (!cityStateMember.isValid()) throw new IllegalStateException("成员关系已失效");
                    if (!cityStateMember.hasPermission(CityStatePermission.USE_SHOP)) throw new IllegalStateException("没有商店权限");
                },
                RookieCityState.inst().getTransactionService().cityState(
                        cityState.getCityStateBank(), CityStateBank.BalanceType.GMONEY, price),
                this::onPaid);
        if (!result.success()) Util.sendMsg(getBukkitPlayer(), "&c交易失败: " + result.reason());
    }

    public abstract void onPaid();

    @Override
    public void onCancel() {
        back();
    }

    @Override
    public GUI.Type getGUIType() {
        return GUI.Type.SHOP_CONFIRM;
    }
}
