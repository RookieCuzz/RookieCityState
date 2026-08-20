package com.cuzz.rookiecitystate.world;

public record ExitResult(boolean success, String reason) {
    public static ExitResult ok() { return new ExitResult(true, ""); }
    public static ExitResult failed(String reason) { return new ExitResult(false, reason); }
}
