package com.cuzz.rookiecitystate.social;

import org.bukkit.configuration.file.YamlConfiguration;

import java.time.ZoneId;

public final class CitySocialConfig {
    private final int schemaVersion;
    private final CitySocialClock clock;
    private final int qualificationSeconds;
    private final int weeklyLikeLimit;
    private final int hotWindowDays;
    private final long visitorWeight;
    private final long likeWeight;
    private final int retentionDays;

    private CitySocialConfig(int schemaVersion, CitySocialClock clock, int qualificationSeconds,
                             int weeklyLikeLimit, int hotWindowDays, long visitorWeight,
                             long likeWeight, int retentionDays) {
        this.schemaVersion = schemaVersion;
        this.clock = clock;
        this.qualificationSeconds = qualificationSeconds;
        this.weeklyLikeLimit = weeklyLikeLimit;
        this.hotWindowDays = hotWindowDays;
        this.visitorWeight = visitorWeight;
        this.likeWeight = likeWeight;
        this.retentionDays = retentionDays;
    }

    public static CitySocialConfig load(YamlConfiguration yaml) {
        int schema = positive(yaml, "schema_version");
        if (schema != 1) throw new IllegalArgumentException("city_social.yml schema_version 仅支持 1");
        String timezone = required(yaml, "time.timezone");
        int hour = yaml.getInt("time.reset_hour", -1);
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("time.reset_hour 必须为 0-23");
        CitySocialClock clock;
        try { clock = new CitySocialClock(ZoneId.of(timezone), hour); }
        catch (RuntimeException error) { throw new IllegalArgumentException("time.timezone 无效", error); }
        int qualification = positive(yaml, "visit.qualification_seconds");
        int weeklyLimit = positive(yaml, "like.weekly_limit");
        int window = positive(yaml, "hot.window_days");
        long visitorWeight = nonNegativeLong(yaml, "hot.visitor_weight");
        long likeWeight = nonNegativeLong(yaml, "hot.like_weight");
        if (visitorWeight == 0L && likeWeight == 0L) {
            throw new IllegalArgumentException("热门权重不能同时为 0");
        }
        int retention = positive(yaml, "storage.detail_retention_days");
        if (retention < window) throw new IllegalArgumentException("明细保留天数不能小于热度窗口");
        return new CitySocialConfig(schema, clock, qualification, weeklyLimit, window,
                visitorWeight, likeWeight, retention);
    }

    private static int positive(YamlConfiguration yaml, String path) {
        int value = yaml.getInt(path, 0);
        if (value < 1) throw new IllegalArgumentException(path + " 必须为正整数");
        return value;
    }

    private static long nonNegativeLong(YamlConfiguration yaml, String path) {
        long value = yaml.getLong(path, -1L);
        if (value < 0L) throw new IllegalArgumentException(path + " 不能为负数");
        return value;
    }

    private static String required(YamlConfiguration yaml, String path) {
        String value = yaml.getString(path);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(path + " 不能为空");
        return value.trim();
    }

    public int schemaVersion() { return schemaVersion; }
    public CitySocialClock clock() { return clock; }
    public int qualificationSeconds() { return qualificationSeconds; }
    public int weeklyLikeLimit() { return weeklyLikeLimit; }
    public int hotWindowDays() { return hotWindowDays; }
    public long visitorWeight() { return visitorWeight; }
    public long likeWeight() { return likeWeight; }
    public int retentionDays() { return retentionDays; }
}
