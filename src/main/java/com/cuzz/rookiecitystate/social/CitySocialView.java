package com.cuzz.rookiecitystate.social;

import java.util.UUID;

public record CitySocialView(UUID cityStateId, Status status, String error,
                             long totalLikes, int recentVisitors, int recentLikes,
                             long hotScore, int hotRank, boolean qualified,
                             boolean likedThisWeek, int votesUsed, int votesRemaining,
                             long qualificationExpiresAt) {
    public enum Status { READY, ERROR }
}
