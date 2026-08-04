package com.cuzz.rookiecitystate.request;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.request.entities.TpAllRequest;
import com.cuzz.rookiecitystate.request.entities.JoinRequest;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface Request<T1 extends Sender, T2 extends Receiver> {
    enum Type {
        TP_ALL(TpAllRequest.class),
        JOIN(JoinRequest.class);

        private Class<? extends Request> clazz;

        Type(Class<? extends Request> clazz) {
            this.clazz = clazz;
        }

        public Class<? extends Request> getClazz() {
            return clazz;
        }
    }

    T1 getSender();

    T2 getReceiver();

    long getCreationTime();

    UUID getUuid();

    Type getType();

    void onSave(@NotNull ConfigurationSection section);

    void onLoad(@NotNull ConfigurationSection section);

    boolean isValid();

    default void delete() {
        RookieCityState.inst().getRequestManager().deleteRequest(this);
    }

    default void send() {
        RookieCityState.inst().getRequestManager().sendRequest(this);
    }
}
