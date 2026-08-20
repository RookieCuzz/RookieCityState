package com.cuzz.rookiecitystate.world;

public record EnterResult(boolean success, String reason) {
    public static EnterResult ok() { return new EnterResult(true, ""); }
    public static EnterResult failed(String reason) { return new EnterResult(false, reason); }
}
