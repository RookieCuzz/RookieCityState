package com.cuzz.rookiecitystate.guardian;

import org.junit.jupiter.api.Test;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class GuardianBlueprintAssetTest {
    @Test void allSevenPublishedBlueprintsAreValidAndFormsDiffer() throws IOException {
        Map<String, String> hashes = new HashMap<>();
        for (GuardianBundledAssets.Asset asset : GuardianBundledAssets.MODELS) {
            String id = asset.id();
            String resource = "/" + asset.resourcePath();
            try (InputStream input = getClass().getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                byte[] bytes = input.readAllBytes();
                GuardianBlueprintInstaller.validateBlueprint(id, bytes);
                String hash = sha(bytes);
                assertEquals(asset.sha256(), hash, id);
                hashes.put(id, hash);
            }
        }
        assertEquals(7, hashes.size());
        for (int species = 1; species <= 3; species++) {
            assertNotEquals(hashes.get("rcs_guardian_" + species + "_baby_r1"),
                    hashes.get("rcs_guardian_" + species + "_adult_r1"));
        }
    }

    @Test void manifestAndDefaultConfigurationReferenceExactlyThePublishedModels() throws IOException {
        YamlConfiguration manifest;
        try (InputStream input = getClass().getResourceAsStream(
                "/modelengine/blueprints/rookiecitystate/r1/manifest.yml")) {
            assertNotNull(input);
            manifest = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        assertEquals(GuardianBundledAssets.REVISION, manifest.getInt("revision"));
        assertEquals("EMBEDDED_PNG", manifest.getString("textures"));
        assertEquals(GuardianBundledAssets.modelIds(), manifest.getConfigurationSection("models")
                .getKeys(false).stream().toList());
        GuardianBundledAssets.MODELS.forEach(asset ->
                assertEquals(asset.sha256(), manifest.getString("models." + asset.id())));

        YamlConfiguration defaults;
        try (InputStream input = getClass().getResourceAsStream("/resources/guardian_beast.yml")) {
            assertNotNull(input);
            defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        java.util.LinkedHashSet<String> configured = new java.util.LinkedHashSet<>();
        configured.add(defaults.getString("models.egg"));
        for (int species = 1; species <= 3; species++) {
            configured.add(defaults.getString("species." + species + ".baby_model"));
            configured.add(defaults.getString("species." + species + ".adult_model"));
        }
        assertEquals(new java.util.LinkedHashSet<>(GuardianBundledAssets.modelIds()), configured);
    }

    private String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
