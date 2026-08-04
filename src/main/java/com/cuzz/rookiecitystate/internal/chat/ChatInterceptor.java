package com.cuzz.rookiecitystate.internal.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatInterceptor implements Listener {
    private static final Map<UUID, ChatInterceptor> ACTIVE = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private final Player player;
    private final boolean onlyFirst;
    private final long timeoutSeconds;
    private final ChatListener listener;
    private final AtomicBoolean consumed = new AtomicBoolean();
    private BukkitTask timeoutTask;

    private ChatInterceptor(Builder builder) {
        this.plugin = builder.plugin;
        this.player = builder.player;
        this.onlyFirst = builder.onlyFirst;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.listener = builder.listener;
    }

    public void register() {
        ChatInterceptor old = ACTIVE.put(player.getUniqueId(), this);
        if (old != null) old.close(false);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (timeoutSeconds > 0) {
            timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!ACTIVE.remove(player.getUniqueId(), this)) return;
                HandlerList.unregisterAll(this);
                listener.onTimeout();
            }, timeoutSeconds * 20L);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
        event.setCancelled(true);
        if (onlyFirst && !consumed.compareAndSet(false, true)) return;
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (onlyFirst) unregister();
            if (message.equalsIgnoreCase("cancel") || message.equals("取消")) {
                unregister();
                listener.onCancel();
            } else {
                listener.onChat(message);
            }
        });
    }

    public void unregister() {
        close(true);
    }

    private void close(boolean removeActiveSession) {
        if (removeActiveSession) ACTIVE.remove(player.getUniqueId(), this);
        HandlerList.unregisterAll(this);
        if (timeoutTask != null) timeoutTask.cancel();
    }

    public static void unregisterAll(Plugin plugin) {
        for (ChatInterceptor interceptor : ACTIVE.values().toArray(ChatInterceptor[]::new)) {
            if (interceptor.plugin.equals(plugin)) interceptor.unregister();
        }
    }

    public static final class Builder {
        private Plugin plugin;
        private Player player;
        private boolean onlyFirst;
        private long timeoutSeconds;
        private ChatListener listener;

        public Builder plugin(Plugin plugin) { this.plugin = plugin; return this; }
        public Builder player(Player player) { this.player = player; return this; }
        public Builder onlyFirst(boolean onlyFirst) { this.onlyFirst = onlyFirst; return this; }
        public Builder timeout(long seconds) { this.timeoutSeconds = seconds; return this; }
        public Builder chatListener(ChatListener listener) { this.listener = listener; return this; }
        public ChatInterceptor build() {
            if (plugin == null || player == null || listener == null) throw new IllegalStateException("聊天输入会话参数不完整");
            return new ChatInterceptor(this);
        }
    }
}
