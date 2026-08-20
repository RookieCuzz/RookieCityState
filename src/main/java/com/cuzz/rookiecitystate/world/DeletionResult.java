package com.cuzz.rookiecitystate.world;

public record DeletionResult(boolean success, String reason, String archiveId) {
    public static DeletionResult ok(String archiveId) { return new DeletionResult(true, "", archiveId); }
    public static DeletionResult failed(String reason) { return new DeletionResult(false, reason, null); }
}
