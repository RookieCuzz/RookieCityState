package com.cuzz.rookiecitystate.wishtree;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

public final class WishTreeClock {
    private final ZoneId zone;
    private final int resetHour;

    public WishTreeClock(ZoneId zone, int resetHour) {
        this.zone = zone;
        this.resetHour = resetHour;
    }

    public String day(long epochMillis) {
        ZonedDateTime now = Instant.ofEpochMilli(epochMillis).atZone(zone).minusHours(resetHour);
        return now.toLocalDate().toString();
    }

    public String week(long epochMillis) {
        ZonedDateTime shifted = Instant.ofEpochMilli(epochMillis).atZone(zone).minusHours(resetHour);
        LocalDate monday = shifted.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.toString();
    }

    public ZoneId zone() { return zone; }
    public int resetHour() { return resetHour; }
}
