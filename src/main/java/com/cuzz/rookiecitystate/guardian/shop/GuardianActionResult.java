package com.cuzz.rookiecitystate.guardian.shop;

public record GuardianActionResult(Status status, String message) {
    public enum Status { SUCCESS, CITY_UNAVAILABLE, NOT_MEMBER, NOT_EQUIPPED, FORM_LOCKED, TOO_FAR, COOLDOWN, BUSY, FAILED }
    public boolean success() { return status == Status.SUCCESS; }
    public static GuardianActionResult failed(Status status, String message) { return new GuardianActionResult(status, message); }
}
