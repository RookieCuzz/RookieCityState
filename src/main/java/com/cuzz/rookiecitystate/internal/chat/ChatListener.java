package com.cuzz.rookiecitystate.internal.chat;

public interface ChatListener {
    void onChat(String message);

    default void onTimeout() { }
    default void onCancel() { }
}
