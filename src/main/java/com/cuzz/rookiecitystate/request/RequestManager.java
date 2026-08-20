package com.cuzz.rookiecitystate.request;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RequestManager {
    private record EndpointKey(String type, UUID uuid) { }

    private final RookieCityState plugin = RookieCityState.inst();
    private final Map<UUID, Request<?, ?>> requests = new HashMap<>();
    private final Map<EndpointKey, List<Request<?, ?>>> sent = new HashMap<>();
    private final Map<EndpointKey, List<Request<?, ?>>> received = new HashMap<>();

    public synchronized Collection<Request<?, ?>> getRequests() { return List.copyOf(requests.values()); }

    public synchronized void sendRequest(@NotNull Request<?, ?> request) {
        if (requests.containsKey(request.getUuid())) throw new IllegalStateException("请求已经发送");
        if (!safeValid(request)) throw new IllegalArgumentException("请求当前无效");

        List<Request<?, ?>> replaced = new ArrayList<>();
        if (request.getType() == Request.Type.TP_ALL) {
            for (Request old : getReceivedRequests(request.getReceiver())) {
                if (old.getType() == Request.Type.TP_ALL) replaced.add(old);
            }
        }

        File file = requestFile(request.getUuid());
        YamlConfiguration yaml = new YamlConfiguration();
        request.onSave(yaml);
        YamlFiles.save(yaml, file);
        index(request);
        for (Request<?, ?> old : replaced) {
            try {
                deleteRequest(old);
            } catch (RuntimeException exception) {
                unloadRequest(old);
                PluginLogger.warning("旧 TP_ALL 请求文件删除失败，已从内存索引移除: "
                        + old.getUuid() + ": " + exception.getMessage());
            }
        }
    }

    public synchronized void unloadRequest(@NotNull Request<?, ?> request) {
        if (requests.get(request.getUuid()) != request) return;
        requests.remove(request.getUuid());
        remove(sent, endpoint(request.getSender()), request);
        remove(received, endpoint(request.getReceiver()), request);
    }

    public synchronized void deleteRequest(@NotNull Request<?, ?> request) {
        if (requests.get(request.getUuid()) != request) return;
        try {
            Files.deleteIfExists(requestFile(request.getUuid()).toPath());
        } catch (IOException exception) {
            throw new IllegalStateException("请求文件删除失败: " + request.getUuid(), exception);
        }
        unloadRequest(request);
    }

    public synchronized void loadRequests() {
        requests.clear();
        sent.clear();
        received.clear();
        File folder = new File(plugin.getDataFolder(), "data" + File.separator + "requests");
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        for (File file : files) loadRequest(file);
    }

    public int deletePersistedForCity(UUID cityStateId) {
        File folder = new File(plugin.getDataFolder(), "data" + File.separator + "requests");
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return 0;
        int deleted = 0;
        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlFiles.load(file);
                if (!referencesCity(yaml, "sender", cityStateId) && !referencesCity(yaml, "receiver", cityStateId)) {
                    continue;
                }
                Files.deleteIfExists(file.toPath());
                deleted++;
            } catch (RuntimeException | IOException exception) {
                PluginLogger.warning("清理解散城邦请求文件失败 " + file.getName() + ": " + exception.getMessage());
            }
        }
        return deleted;
    }

    private boolean referencesCity(YamlConfiguration yaml, String endpoint, UUID cityStateId) {
        String type = yaml.getString(endpoint + ".type", "");
        String cityId = type.equalsIgnoreCase("CITY_STATE_MEMBER")
                ? yaml.getString(endpoint + ".city_state_uuid")
                : type.equalsIgnoreCase("CITY_STATE") ? yaml.getString(endpoint + ".uuid") : null;
        return cityStateId.toString().equals(cityId);
    }

    private void loadRequest(File file) {
        try {
            YamlConfiguration yaml = YamlFiles.load(file);
            Request.Type type = Request.Type.valueOf(yaml.getString("type", ""));
            Request<?, ?> request = type.getClazz().getDeclaredConstructor().newInstance();
            request.onLoad(yaml);
            if (requests.containsKey(request.getUuid())) throw new IllegalArgumentException("请求 UUID 重复");
            if (!safeValid(request)) throw new IllegalArgumentException("请求已过期或关联对象已失效");
            if (request.getType() == Request.Type.TP_ALL) {
                boolean superseded = false;
                for (Request<?, ?> old : getReceivedRequests(request.getReceiver())) {
                    if (old.getType() == Request.Type.TP_ALL) {
                        if (old.getCreationTime() <= request.getCreationTime()) {
                            unloadRequest(old);
                        } else {
                            superseded = true;
                        }
                    }
                }
                if (superseded) throw new IllegalArgumentException("TP_ALL 请求已被更新请求取代");
            }
            index(request);
        } catch (Throwable exception) {
            PluginLogger.warning("跳过无效请求 " + file.getName() + ": " + exception.getMessage());
        }
    }

    private boolean safeValid(Request<?, ?> request) {
        try { return request.getSender() != null && request.getReceiver() != null && request.isValid(); }
        catch (RuntimeException exception) { return false; }
    }

    private void index(Request<?, ?> request) {
        requests.put(request.getUuid(), request);
        sent.computeIfAbsent(endpoint(request.getSender()), ignored -> new ArrayList<>()).add(request);
        received.computeIfAbsent(endpoint(request.getReceiver()), ignored -> new ArrayList<>()).add(request);
    }

    private <K> void remove(Map<K, List<Request<?, ?>>> map, K key, Request<?, ?> request) {
        List<Request<?, ?>> values = map.get(key);
        if (values == null) return;
        values.remove(request);
        if (values.isEmpty()) map.remove(key);
    }

    public synchronized List<Request> getSentRequests(@NotNull Sender senderEndpoint) {
        return new ArrayList<>(sent.getOrDefault(endpoint(senderEndpoint), List.of()));
    }

    public synchronized List<Request> getReceivedRequests(@NotNull Receiver receiverEndpoint) {
        return new ArrayList<>(received.getOrDefault(endpoint(receiverEndpoint), List.of()));
    }

    public synchronized Request<?, ?> getRequest(@NotNull UUID uuid) { return requests.get(uuid); }

    private EndpointKey endpoint(Object value) {
        if (value instanceof CityState cityState) return new EndpointKey("CITY_STATE", cityState.getUuid());
        if (value instanceof CityStateMember member) return new EndpointKey("CITY_STATE_MEMBER", member.getUuid());
        if (value instanceof CityStatePlayer player) return new EndpointKey("CITY_STATE_PLAYER", player.getUuid());
        throw new IllegalArgumentException("未知请求端点: " + value);
    }

    private File requestFile(UUID uuid) {
        return new File(plugin.getDataFolder(), "data" + File.separator + "requests" + File.separator + uuid + ".yml");
    }
}
