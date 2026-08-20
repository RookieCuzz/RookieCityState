package com.cuzz.rookiecitystate.guardian;

import java.time.Instant;
import java.time.ZoneId;

public final class GuardianClock {
    private final ZoneId zone;
    private final int resetHour;

    public GuardianClock(ZoneId zone, int resetHour) {
        this.zone = zone;
        this.resetHour = resetHour;
    }

    public String day(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone).minusHours(resetHour).toLocalDate().toString();
    }
}
