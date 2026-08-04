package com.cuzz.rookiecitystate.thirdparty.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;

public class VaultEconomy {
    private Economy economy;

    public VaultEconomy(Economy economy) {
        this.economy = economy;
    }

    public boolean has(Player player, double amount) {
        requireNonNegative(amount);

        return getBalance(player) >= amount;
    }

    public boolean withdraw(Player player, double amount) {
        requireNonNegative(amount);
        if (amount == 0D) return true;
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    public boolean deposit(Player player, double amount) {
        requireNonNegative(amount);
        if (amount == 0D) return true;
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    public double getBalance(Player player) {
        return economy.getBalance(player);
    }

    private void requireNonNegative(double amount) {
        if (!Double.isFinite(amount) || amount < 0D) throw new IllegalArgumentException("数量不能为负数: " + amount);
    }
}
