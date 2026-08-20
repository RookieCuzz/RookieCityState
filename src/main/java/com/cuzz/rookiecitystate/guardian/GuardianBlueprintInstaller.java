package com.cuzz.rookiecitystate.guardian;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GuardianBlueprintInstaller {
    private static final List<String> LIVE_ANIMATIONS = List.of("idle", "walk", "fly_start", "fly_loop", "fly_end");
    private final RookieCityState plugin;
    private final GuardianModelBackend modelBackend;
    private volatile GuardianModelInstallStatus lastStatus = new GuardianModelInstallStatus(false, false, 0, Map.of(),
            List.of("尚未检查灵兽模型"));

    public GuardianBlueprintInstaller(RookieCityState plugin, GuardianModelBackend modelBackend) {
        this.plugin = plugin;
        this.modelBackend = modelBackend;
    }

    public synchronized GuardianModelInstallStatus installMissing() {
        Plugin modelEngine = plugin.getServer().getPluginManager().getPlugin("ModelEngine");
        if (modelEngine == null) return remember(new GuardianModelInstallStatus(false, false, 0, Map.of(),
                List.of("ModelEngine 插件未加载")));
        File destination = new File(modelEngine.getDataFolder(), "blueprints/rookiecitystate/r1");
        List<String> errors = new ArrayList<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        int installed = 0;
        try { Files.createDirectories(destination.toPath()); }
        catch (IOException error) { return remember(new GuardianModelInstallStatus(false, false, 0, Map.of(), List.of(error.getMessage()))); }

        for (GuardianBundledAssets.Asset asset : GuardianBundledAssets.MODELS) {
            String id = asset.id();
            try (InputStream input = plugin.getResource(asset.resourcePath())) {
                if (input == null) { errors.add("缺少内置蓝图: " + id); continue; }
                byte[] bytes = input.readAllBytes();
                validateBlueprint(id, bytes);
                String bundledHash = sha256(bytes);
                if (!asset.sha256().equals(bundledHash)) {
                    throw new IllegalArgumentException("内置蓝图 SHA-256 校验失败: " + bundledHash);
                }
                File target = new File(destination, id + ".bbmodel");
                if (!target.exists()) {
                    Files.copy(new java.io.ByteArrayInputStream(bytes), target.toPath());
                    installed++;
                }
                byte[] installedBytes = Files.readAllBytes(target.toPath());
                validateBlueprint(id, installedBytes);
                hashes.put(id, sha256(installedBytes));
            } catch (Exception error) {
                errors.add(id + ": " + error.getMessage());
            }
        }
        validateDistinct(hashes, errors);
        YamlConfiguration manifest = new YamlConfiguration();
        manifest.set("revision", GuardianBundledAssets.REVISION);
        manifest.set("generated_at", System.currentTimeMillis());
        manifest.set("destination", destination.getAbsolutePath());
        hashes.forEach((id, hash) -> manifest.set("models." + id + ".sha256", hash));
        manifest.set("installed_files", installed);
        manifest.set("errors", errors);
        YamlFiles.save(manifest, new File(plugin.getDataFolder(), "data/guardian_models/r1-manifest.yml"));
        boolean registered = errors.isEmpty() && modelsRegistered();
        return remember(new GuardianModelInstallStatus(errors.isEmpty(), registered, installed, Map.copyOf(hashes), List.copyOf(errors)));
    }

    public synchronized GuardianModelInstallStatus status() {
        Plugin modelEngine = plugin.getServer().getPluginManager().getPlugin("ModelEngine");
        if (modelEngine == null) return remember(new GuardianModelInstallStatus(false, false, 0, Map.of(), List.of("ModelEngine 插件未加载")));
        File destination = new File(modelEngine.getDataFolder(), "blueprints/rookiecitystate/r1");
        List<String> errors = new ArrayList<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String id : GuardianBundledAssets.modelIds()) {
            File file = new File(destination, id + ".bbmodel");
            if (!file.exists()) { errors.add("未安装: " + id); continue; }
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                validateBlueprint(id, bytes);
                hashes.put(id, sha256(bytes));
            } catch (Exception error) { errors.add(id + ": " + error.getMessage()); }
        }
        validateDistinct(hashes, errors);
        return remember(new GuardianModelInstallStatus(errors.isEmpty(), errors.isEmpty() && modelsRegistered(), 0,
                Map.copyOf(hashes), List.copyOf(errors)));
    }

    public GuardianModelInstallStatus currentStatus() { return lastStatus; }

    public synchronized GuardianModelInstallStatus refreshRegistration() {
        GuardianModelInstallStatus current = lastStatus;
        return remember(new GuardianModelInstallStatus(current.assetsValid(), current.assetsValid() && modelsRegistered(),
                0, current.hashes(), current.errors()));
    }

    private GuardianModelInstallStatus remember(GuardianModelInstallStatus status) {
        this.lastStatus = status;
        return status;
    }

    public boolean modelsRegistered() {
        for (String id : GuardianBundledAssets.modelIds()) if (!modelBackend.isRegistered(id)) return false;
        return true;
    }

    public static List<String> modelIds() { return GuardianBundledAssets.modelIds(); }

    static void validateBlueprint(String expectedId, byte[] bytes) {
        JsonObject root;
        try { root = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException error) { throw new IllegalArgumentException("JSON 无法解析", error); }
        if (!root.has("meta") || !root.getAsJsonObject("meta").has("box_uv")
                || root.getAsJsonObject("meta").get("box_uv").getAsBoolean()) {
            throw new IllegalArgumentException("box_uv 必须为 false");
        }
        if (!expectedId.equals(root.get("model_identifier").getAsString())) {
            throw new IllegalArgumentException("model_identifier 应为 " + expectedId);
        }
        JsonArray textures = root.getAsJsonArray("textures");
        if (textures == null || textures.isEmpty()) throw new IllegalArgumentException("模型缺少贴图");
        for (JsonElement texture : textures) {
            JsonObject object = texture.getAsJsonObject();
            if (!object.has("source") || !object.get("source").getAsString().startsWith("data:image/png;base64,")) {
                throw new IllegalArgumentException("模型包含未嵌入的贴图");
            }
        }
        JsonArray animations = root.getAsJsonArray("animations");
        List<String> names = new ArrayList<>();
        if (animations != null) for (JsonElement element : animations) names.add(element.getAsJsonObject().get("name").getAsString());
        if (expectedId.contains("egg")) {
            for (String name : List.of("idle", "interact", "eclode", "remove"))
                if (!names.contains(name)) throw new IllegalArgumentException("蛋缺少动画 " + name);
        } else {
            for (String name : LIVE_ANIMATIONS) if (!names.contains(name))
                throw new IllegalArgumentException("活体缺少公共动画 " + name);
            if (!names.contains("death_animation")) throw new IllegalArgumentException("活体缺少 death_animation");
        }
    }

    private static void validateDistinct(Map<String, String> hashes, List<String> errors) {
        for (int i = 1; i <= 3; i++) {
            String baby = hashes.get("rcs_guardian_" + i + "_baby_r1");
            String adult = hashes.get("rcs_guardian_" + i + "_adult_r1");
            if (baby != null && baby.equals(adult)) errors.add("第 " + i + " 种幼体与成年体内容完全相同");
        }
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
