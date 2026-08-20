package com.cuzz.rookiecitystate.guardian;

public record FeedResult(Status status, int nourishment, int contribution, boolean favorite,
                         boolean dailyCompleted, int oldLevel, int newLevel, String message) {
    public enum Status { SUCCESS, INVALID_HAND, NOT_MEMBER, CITY_UNAVAILABLE, SPECIES_NOT_SELECTED,
        DAILY_LIMIT, INVALID_FOOD, MODULE_UNAVAILABLE, BUSY, SAVE_FAILED }

    public static FeedResult failed(Status status, String message) {
        return new FeedResult(status, 0, 0, false, false, 0, 0, message);
    }
}
