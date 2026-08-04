package com.cuzz.rookiecitystate.internal.util;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {
    public static final DateTimeFormatter YMD_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private TimeUtil() {
    }
}
