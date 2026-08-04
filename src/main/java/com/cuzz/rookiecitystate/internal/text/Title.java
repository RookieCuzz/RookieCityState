package com.cuzz.rookiecitystate.internal.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class Title {
    public enum Type { TITLE, SUBTITLE }

    private final Type type;
    private final String text;

    private Title(Type type, String text) {
        this.type = type;
        this.text = text;
    }

    public void send(Player player) {
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(text.replace('§', '&'));
        Component title = type == Type.TITLE ? component : Component.empty();
        Component subtitle = type == Type.SUBTITLE ? component : Component.empty();
        player.showTitle(net.kyori.adventure.title.Title.title(title, subtitle,
                net.kyori.adventure.title.Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1))));
    }

    public static final class Builder {
        private Type type = Type.TITLE;
        private String text = "";

        public Builder type(Type type) { this.type = type; return this; }
        public Builder text(String text) { this.text = text == null ? "" : text; return this; }
        public Builder colored() { return this; }
        public Title build() { return new Title(type, text); }
    }
}
