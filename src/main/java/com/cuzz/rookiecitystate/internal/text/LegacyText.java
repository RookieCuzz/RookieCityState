package com.cuzz.rookiecitystate.internal.text;

import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.List;

public final class LegacyText {
    private LegacyText() {
    }

    public static String getColoredText(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> getColoredTexts(List<String> texts) {
        List<String> result = new ArrayList<>();
        for (String text : texts) result.add(getColoredText(text));
        return result;
    }

    public static String secondToStr(long seconds, DateTimeUnit unit) {
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        StringBuilder value = new StringBuilder();
        if (days > 0) value.append(days).append(unit.day());
        if (hours > 0) value.append(hours).append(unit.hour());
        if (minutes > 0) value.append(minutes).append(unit.minute());
        if (seconds > 0 || value.isEmpty()) value.append(seconds).append(unit.second());
        return value.toString();
    }
}
