package com.cuzz.rookiecitystate.internal.text;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private MessageService() {
    }

    public static void sendColoredMessage(CommandSender sender, String message) {
        sender.sendMessage(LEGACY.deserialize((message == null ? "" : message).replace('§', '&')));
    }

    public static void broadcastColoredMessage(String message) {
        Bukkit.getServer().sendMessage(LEGACY.deserialize((message == null ? "" : message).replace('§', '&')));
    }

    public static boolean isTitleEnabled() {
        return true;
    }

    public static void sendTitle(Player player, Title title) {
        title.send(player);
    }
}
