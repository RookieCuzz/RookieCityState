package com.cuzz.rookiecitystate.guardian.shop;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

public final class GuardianShopClock {
    private final ZoneId zone;
    private final int resetHour;

    public GuardianShopClock(ZoneId zone, int resetHour) {
        this.zone = zone;
        this.resetHour = resetHour;
    }

    public String week(long epochMillis) {
        LocalDateTime local = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone);
        LocalDate effective = local.toLocalTime().isBefore(LocalTime.of(resetHour, 0))
                ? local.toLocalDate().minusDays(1) : local.toLocalDate();
        return effective.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
    }

    public long nextWeekStart(long epochMillis) {
        LocalDate monday = LocalDate.parse(week(epochMillis));
        return monday.plusWeeks(1).atTime(resetHour, 0).atZone(zone).toInstant().toEpochMilli();
    }

    public ZoneId zone() { return zone; }
    public int resetHour() { return resetHour; }
}
