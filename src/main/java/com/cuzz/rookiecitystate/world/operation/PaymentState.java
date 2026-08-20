package com.cuzz.rookiecitystate.world.operation;

public enum PaymentState {
    NOT_CHARGED,
    CHARGE_INTENT,
    CHARGED,
    REFUND_PENDING,
    REFUNDED,
    PAYMENT_RECONCILIATION_REQUIRED
}
