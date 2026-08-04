package com.cuzz.rookiecitystate.citystate;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

public final class CityStateBank {
    public enum BalanceType { GMONEY }

    private final CityState cityState;
    private final Map<BalanceType, BigDecimal> balances = new EnumMap<>(BalanceType.class);
    private final ConfigurationSection section;

    CityStateBank(CityState cityState) {
        this.cityState = cityState;
        ConfigurationSection existing = cityState.getYaml().getConfigurationSection("bank");
        section = existing == null ? cityState.getYaml().createSection("bank") : existing;
        for (String key : section.getKeys(false)) {
            try {
                balances.put(BalanceType.valueOf(key), new BigDecimal(section.getString(key, "0")));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("无效城邦余额: bank." + key, exception);
            }
        }
    }

    public synchronized boolean has(@NotNull BalanceType type, double value) {
        return has(type, BigDecimal.valueOf(value));
    }

    public synchronized boolean has(@NotNull BalanceType type, @NotNull BigDecimal value) {
        requireNonNegative(value);
        return getBalance(type).compareTo(value) >= 0;
    }

    public synchronized void deposit(@NotNull BalanceType type, double amount) {
        deposit(type, BigDecimal.valueOf(amount));
    }

    public synchronized void deposit(@NotNull BalanceType type, @NotNull BigDecimal amount) {
        requireNonNegative(amount);
        if (amount.signum() == 0) return;
        setBalance(type, getBalance(type).add(amount));
    }

    public synchronized void withdraw(@NotNull BalanceType type, double amount) {
        withdraw(type, BigDecimal.valueOf(amount));
    }

    public synchronized void withdraw(@NotNull BalanceType type, @NotNull BigDecimal amount) {
        requireNonNegative(amount);
        if (amount.signum() == 0) return;
        if (!has(type, amount)) throw new IllegalStateException("城邦余额不足");
        setBalance(type, getBalance(type).subtract(amount));
    }

    public synchronized void setBalance(@NotNull BalanceType type, @NotNull BigDecimal value) {
        requireNonNegative(value);
        BigDecimal previous = getBalance(type);
        section.set(type.name(), value.toPlainString());
        try {
            cityState.save();
        } catch (RuntimeException exception) {
            section.set(type.name(), previous.toPlainString());
            throw exception;
        }
        balances.put(type, value);
    }

    public synchronized BigDecimal getBalance(@NotNull BalanceType type) {
        return balances.getOrDefault(type, BigDecimal.ZERO);
    }

    private void requireNonNegative(BigDecimal value) {
        if (value.signum() < 0) throw new IllegalArgumentException("数量不能为负数");
    }
}
