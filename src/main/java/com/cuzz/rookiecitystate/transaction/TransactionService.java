package com.cuzz.rookiecitystate.transaction;

import com.cuzz.rookiecitystate.citystate.CityStateBank;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.thirdparty.economy.PlayerPointsEconomy;
import com.cuzz.rookiecitystate.thirdparty.economy.VaultEconomy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;

public final class TransactionService {
    @FunctionalInterface
    public interface CheckedAction { void run() throws Exception; }

    public interface Payment {
        void validate();
        boolean charge();
        boolean refund();
    }

    public record Result(boolean success, String reason, Throwable cause) {
        public static Result successful() { return new Result(true, "", null); }
    }

    public Result execute(String description, CheckedAction validation, Payment payment, CheckedAction reward) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("交易只能在服务器主线程执行");
        boolean charged = false;
        try {
            validation.run();
            payment.validate();
            if (!payment.charge()) throw new IllegalStateException("扣款服务返回失败");
            charged = true;
            reward.run();
            return Result.successful();
        } catch (Throwable failure) {
            if (charged) {
                try {
                    if (!payment.refund()) {
                        PluginLogger.error("交易退款失败 [" + description + "]，需要人工处理。原因: " + failure.getMessage());
                    }
                } catch (Throwable refundFailure) {
                    failure.addSuppressed(refundFailure);
                    PluginLogger.error("交易退款抛出异常 [" + description + "]，需要人工处理。",
                            refundFailure instanceof RuntimeException runtime ? runtime : new RuntimeException(refundFailure));
                }
            }
            PluginLogger.warning("交易失败 [" + description + "]: " + failure.getMessage());
            return new Result(false, failure.getMessage() == null ? "未知错误" : failure.getMessage(), failure);
        }
    }

    public Payment vault(VaultEconomy economy, Player player, double amount) {
        if (!Double.isFinite(amount) || amount < 0D) throw new IllegalArgumentException("价格非法");
        return new Payment() {
            public void validate() { if (!economy.has(player, amount)) throw new IllegalStateException("金币余额不足"); }
            public boolean charge() { return economy.withdraw(player, amount); }
            public boolean refund() { return economy.deposit(player, amount); }
        };
    }

    public Payment points(PlayerPointsEconomy economy, Player player, int amount) {
        if (amount < 0) throw new IllegalArgumentException("价格非法");
        return new Payment() {
            public void validate() { if (!economy.has(player, amount)) throw new IllegalStateException("点券余额不足"); }
            public boolean charge() { return economy.withdraw(player, amount); }
            public boolean refund() { return economy.deposit(player, amount); }
        };
    }

    public Payment cityState(CityStateBank bank, CityStateBank.BalanceType type, BigDecimal amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("价格非法");
        return new Payment() {
            public void validate() { if (!bank.has(type, amount)) throw new IllegalStateException("城邦币余额不足"); }
            public boolean charge() { bank.withdraw(type, amount); return true; }
            public boolean refund() { bank.deposit(type, amount); return true; }
        };
    }
}
