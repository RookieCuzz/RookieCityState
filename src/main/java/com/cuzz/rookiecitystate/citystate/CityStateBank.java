package com.cuzz.rookiecitystate.citystate;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.HexFormat;
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
                BalanceType type = BalanceType.valueOf(key);
                balances.put(type, new BigDecimal(section.getString(key, "0")));
            } catch (IllegalArgumentException exception) {
                if (!key.equals("credit_ledger")) {
                    throw new IllegalArgumentException("无效城邦余额: bank." + key, exception);
                }
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

    /** Atomically credits the balance and records a deterministic idempotency key in the city YAML. */
    public synchronized boolean creditOnce(@NotNull String idempotencyKey, @NotNull BalanceType type,
                                           @NotNull BigDecimal amount) {
        requireNonNegative(amount);
        if (idempotencyKey.isBlank()) throw new IllegalArgumentException("幂等键不能为空");
        String ledgerPath = "credit_ledger." + digest(idempotencyKey);
        if (section.getConfigurationSection(ledgerPath) != null) return false;
        BigDecimal previous = getBalance(type);
        BigDecimal next = previous.add(amount);
        section.set(type.name(), next.toPlainString());
        section.set(ledgerPath + ".key", idempotencyKey);
        section.set(ledgerPath + ".type", type.name());
        section.set(ledgerPath + ".amount", amount.toPlainString());
        section.set(ledgerPath + ".credited_at", System.currentTimeMillis());
        try {
            cityState.save();
        } catch (RuntimeException error) {
            section.set(type.name(), previous.toPlainString());
            section.set(ledgerPath, null);
            throw error;
        }
        balances.put(type, next);
        return true;
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

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("无法生成城邦入账幂等键", error);
        }
    }
}
