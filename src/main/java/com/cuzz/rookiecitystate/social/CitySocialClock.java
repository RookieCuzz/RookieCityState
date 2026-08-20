package com.cuzz.rookiecitystate.social;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

public final class CitySocialClock {
    private final ZoneId zone;
    private final int resetHour;

    public CitySocialClock(ZoneId zone, int resetHour) {
        this.zone = zone;
        this.resetHour = resetHour;
    }

    public String day(long epochMillis) {
        return shiftedDate(epochMillis).toString();
    }

    public String week(long epochMillis) {
        return shiftedDate(epochMillis)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
    }

    public List<String> recentDays(long epochMillis, int count) {
        LocalDate today = shiftedDate(epochMillis);
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(today.minusDays(i).toString());
        return List.copyOf(result);
    }

    public long nextWeekStart(long epochMillis) {
        LocalDate monday = LocalDate.parse(week(epochMillis)).plusWeeks(1);
        return monday.atTime(resetHour, 0).atZone(zone).toInstant().toEpochMilli();
    }

    public ZoneId zone() { return zone; }
    public int resetHour() { return resetHour; }

    private LocalDate shiftedDate(long epochMillis) {
        ZonedDateTime local = Instant.ofEpochMilli(epochMillis).atZone(zone);
        return local.minusHours(resetHour).toLocalDate();
    }
}
