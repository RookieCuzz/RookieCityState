package com.cuzz.rookiecitystate.listener;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class EssentialsChatListener implements Listener {
    private final RookieCityState plugin = RookieCityState.inst();

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        String source = event.getFormat();
        StringBuilder result = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) != '<') {
                result.append(source.charAt(i));
                continue;
            }
            int end = source.indexOf('>', i);
            if (end < 0) {
                result.append(source.charAt(i));
                continue;
            }
            String token = source.substring(i, end + 1);
            String prefix = placeholderPrefix(token);
            if (prefix == null) {
                result.append(source.charAt(i));
                continue;
            }
            String key = token.substring(prefix.length(), token.length() - 1);
            String replacement;
            if (key.equalsIgnoreCase("is_in_city_state")) {
                replacement = String.valueOf(plugin.getPlaceholderSnapshotService().isInCityState(event.getPlayer().getUniqueId()));
            } else if (!plugin.getPlaceholderSnapshotService().isInCityState(event.getPlayer().getUniqueId())) {
                replacement = MainSettings.getCityStateEssChatNotStr();
            } else {
                replacement = plugin.getPlaceholderSnapshotService().get(event.getPlayer().getUniqueId(), key);
            }
            result.append(replacement == null ? token : replacement);
            i = end;
        }
        event.setFormat(result.toString());
    }

    private String placeholderPrefix(String token) {
        return token.startsWith("<rookiecitystate_") ? "<rookiecitystate_" : null;
    }
}
