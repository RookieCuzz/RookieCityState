package com.cuzz.rookiecitystate.social;

public record CityLikeResult(Status status, String message, long totalLikes, int remainingVotes) {
    public enum Status {
        SUCCESS,
        CITY_UNAVAILABLE,
        OWN_CITY,
        NOT_QUALIFIED,
        ALREADY_LIKED,
        WEEKLY_LIMIT,
        SOCIAL_UNAVAILABLE,
        SAVE_FAILED
    }

    public boolean success() { return status == Status.SUCCESS; }

    public static CityLikeResult failed(Status status, String message, long totalLikes, int remainingVotes) {
        return new CityLikeResult(status, message, totalLikes, remainingVotes);
    }
}
