package com.cuzz.rookiecitystate.guardian;

import java.util.List;
import java.util.Map;

public record GuardianModelInstallStatus(boolean assetsValid, boolean modelsRegistered, int installedFiles,
                                         Map<String, String> hashes, List<String> errors) {
    public boolean ready() { return assetsValid && modelsRegistered && errors.isEmpty(); }
}
