package com.cuzz.rookiecitystate.request;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.CityStateManager;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.player.CityStatePlayerManager;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public abstract class BaseRequest<T1 extends Sender, T2 extends Receiver> implements Request<T1, T2> {
    private long creationTime;
    private UUID uuid;
    private T1 sender;
    private T2 receiver;

    protected BaseRequest() {
    }

    protected BaseRequest(@NotNull T1 sender, @NotNull T2 receiver) {
        if (sender == receiver) throw new IllegalArgumentException("请求发送者与接收者不能相同");
        this.sender = sender;
        this.receiver = receiver;
        uuid = UUID.randomUUID();
        creationTime = System.currentTimeMillis();
    }

    @Override public long getCreationTime() { return creationTime; }
    @Override public UUID getUuid() { return uuid; }
    @Override public T1 getSender() { return sender; }
    @Override public T2 getReceiver() { return receiver; }

    @Override
    public void onSave(@NotNull ConfigurationSection section) {
        section.set("creation_time", creationTime);
        section.set("uuid", uuid.toString());
        section.set("type", getType().name());
        saveEndpoint(section, "sender", sender);
        saveEndpoint(section, "receiver", receiver);
    }

    private void saveEndpoint(ConfigurationSection section, String path, Object endpoint) {
        if (endpoint instanceof CityStatePlayer player) {
            section.set(path + ".type", path.equals("sender") ? Sender.Type.CITY_STATE_PLAYER.name() : Receiver.Type.CITY_STATE_PLAYER.name());
            section.set(path + ".uuid", player.getUuid().toString());
        } else if (endpoint instanceof CityStateMember member) {
            section.set(path + ".type", path.equals("sender") ? Sender.Type.CITY_STATE_MEMBER.name() : Receiver.Type.CITY_STATE_MEMBER.name());
            section.set(path + ".uuid", member.getUuid().toString());
            section.set(path + ".city_state_uuid", member.getCityState().getUuid().toString());
        } else if (endpoint instanceof CityState cityState) {
            section.set(path + ".type", path.equals("sender") ? Sender.Type.CITY_STATE.name() : Receiver.Type.CITY_STATE.name());
            section.set(path + ".uuid", cityState.getUuid().toString());
        } else {
            throw new IllegalArgumentException("不支持的请求端点: " + endpoint);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onLoad(@NotNull ConfigurationSection section) {
        creationTime = section.getLong("creation_time", -1L);
        if (creationTime <= 0) throw new IllegalArgumentException("creation_time 无效");
        uuid = UUID.fromString(Objects.requireNonNull(section.getString("uuid"), "uuid 缺失"));
        sender = (T1) resolveEndpoint(section, "sender", true);
        receiver = (T2) resolveEndpoint(section, "receiver", false);
        if (sender == null || receiver == null) throw new IllegalArgumentException("请求端点不存在");
    }

    private Object resolveEndpoint(ConfigurationSection section, String path, boolean senderEndpoint) {
        String rawType = Objects.requireNonNull(section.getString(path + ".type"), path + ".type 缺失");
        UUID id = UUID.fromString(Objects.requireNonNull(section.getString(path + ".uuid"), path + ".uuid 缺失"));
        CityStateManager cityStates = RookieCityState.inst().getCityStateManager();
        CityStatePlayerManager players = RookieCityState.inst().getCityStatePlayerManager();
        String type = rawType.toUpperCase();
        return switch (type) {
            case "CITY_STATE" -> cityStates.getCityState(id);
            case "CITY_STATE_PLAYER" -> players.getCityStatePlayer(id);
            case "CITY_STATE_MEMBER" -> {
                UUID cityStateId = UUID.fromString(Objects.requireNonNull(section.getString(path + ".city_state_uuid"), path + ".city_state_uuid 缺失"));
                CityState cityState = cityStates.getCityState(cityStateId);
                yield cityState == null ? null : cityState.getMember(id);
            }
            default -> throw new IllegalArgumentException("未知端点类型: " + rawType);
        };
    }
}
