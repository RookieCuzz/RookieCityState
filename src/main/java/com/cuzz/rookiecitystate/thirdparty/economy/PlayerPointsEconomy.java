package com.cuzz.rookiecitystate.thirdparty.economy;

import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.entity.Player;

public class PlayerPointsEconomy {
    private PlayerPointsAPI playerPointsAPI;

    public PlayerPointsEconomy(PlayerPointsAPI playerPointsAPI) {
        this.playerPointsAPI = playerPointsAPI;
    }


    public boolean has(Player player, int amount) {
        requireNonNegative(amount);

        return getBalance(player) >= amount;
    }

    public boolean withdraw(Player player, int amount) {
        requireNonNegative(amount);
        return amount == 0 || playerPointsAPI.take(player.getUniqueId(), amount);
    }

    public boolean deposit(Player player, int amount) {
        requireNonNegative(amount);
        return amount == 0 || playerPointsAPI.give(player.getUniqueId(), amount);
    }

    public int getBalance(Player player) {
        return playerPointsAPI.look(player.getUniqueId());
    }

    private void requireNonNegative(int amount) {
        if (amount < 0) throw new IllegalArgumentException("数量不能为负数: " + amount);
    }
}
