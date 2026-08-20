package com.cuzz.rookiecitystate.guardian;

public record SpeciesSelectionResult(Status status, String message) {
    public enum Status { SUCCESS, NOT_OWNER, CITY_UNAVAILABLE, ALREADY_SELECTED, MODULE_UNAVAILABLE, SAVE_FAILED }
    public static SpeciesSelectionResult failed(Status status, String message) { return new SpeciesSelectionResult(status, message); }
}
