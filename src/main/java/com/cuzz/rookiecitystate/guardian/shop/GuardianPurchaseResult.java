package com.cuzz.rookiecitystate.guardian.shop;

import java.util.UUID;

public record GuardianPurchaseResult(Status status, String message, long remainingContribution, UUID claimId) {
    public enum Status {
        SUCCESS, CITY_UNAVAILABLE, NOT_MEMBER, PRODUCT_UNAVAILABLE, LEVEL_LOCKED, ALREADY_OWNED,
        WEEKLY_LIMIT, INSUFFICIENT_CONTRIBUTION, INBOX_FULL, BUSY, SAVE_FAILED
    }
    public boolean success() { return status == Status.SUCCESS; }
    public static GuardianPurchaseResult failed(Status status, String message) {
        return new GuardianPurchaseResult(status, message, -1L, null);
    }
}
