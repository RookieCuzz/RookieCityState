package com.cuzz.rookiecitystate.internal.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TextService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private TextService() {
    }

    public static Component component(String value) {
        return LEGACY.deserialize((value == null ? "" : value).replace('§', '&'));
    }

    public static void send(CommandSender sender, String value) {
        sender.sendMessage(component(value));
    }

    public static void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.showTitle(net.kyori.adventure.title.Title.title(component(title), component(subtitle),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(fadeIn * 50L),
                        java.time.Duration.ofMillis(stay * 50L),
                        java.time.Duration.ofMillis(fadeOut * 50L))));
    }

    public static String formatTimestamp(long epochMillis, String pattern) {
        return DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis));
    }

    public static String formatDate(long epochMillis) { return DATE.format(Instant.ofEpochMilli(epochMillis)); }
    public static String formatTime(long epochMillis) { return TIME.format(Instant.ofEpochMilli(epochMillis)); }

    public static String formatDecimal(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String formatDecimal(double value) {
        return formatDecimal(BigDecimal.valueOf(value));
    }
}
