package com.cuzz.rookiecitystate;

import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.citystate.member.CityStatePosition;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.placeholder.PlaceholderText;
import com.cuzz.rookiecitystate.internal.text.DateTimeUnit;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import com.cuzz.rookiecitystate.internal.text.TextService;

public class LangHelper {
    public static class Global {
        public static DateTimeUnit getDateTimeUnit() {
            ConfigurationSection section = RookieCityState.inst().getLangYaml().getConfigurationSection("Global");

            return new DateTimeUnit(section.getString("year"), section.getString("month"), section.getString("day"), section.getString("hour"), section.getString("minute"), section.getString("second"));
        }

        public static String formatDateTime(long epochMillis) {
            return TextService.formatTimestamp(epochMillis,
                    RookieCityState.inst().getLangYaml().getString("Global.date_time_format", "yyyy-MM-dd HH:mm:ss"));
        }

        public static String getPrefix() {
            return RookieCityState.inst().getLangYaml().getString("Global.prefix");
        }

        public static String getNickName(@NotNull CityStateMember cityStateMember) {
            ConfigurationSection langSection = RookieCityState.inst().getLangYaml();
            String format = langSection.getString("Global.nick_name");

            return PlaceholderText.replacePlaceholders(format, new PlaceholderContainer()
                    .add("PERMISSION", langSection.getString("CityState.Position." + cityStateMember.getPosition().name().toLowerCase()))
                    .add("NAME", cityStateMember.getName()));
        }

        public static String getPositionName(@NotNull CityStatePosition cityStatePosition) {
            return RookieCityState.inst().getLangYaml().getString("CityState.Position." + cityStatePosition.name().toLowerCase());
        }
    }
}
