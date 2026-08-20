package com.cuzz.rookiecitystate.social;

import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class CitySocialCoreTest {
    @TempDir Path temporary;

    @Test void bundledConfigMatchesV1Rules() {
        CitySocialConfig config = CitySocialConfig.load(YamlFiles.load(
                new File("src/main/resources/resources/city_social.yml")));
        assertEquals(60, config.qualificationSeconds());
        assertEquals(5, config.weeklyLikeLimit());
        assertEquals(7, config.hotWindowDays());
        assertEquals(1L, config.visitorWeight());
        assertEquals(3L, config.likeWeight());
        assertEquals(35, config.retentionDays());
    }

    @Test void dayAndWeekBoundariesUseShanghaiFour() {
        CitySocialClock clock = new CitySocialClock(ZoneId.of("Asia/Shanghai"), 4);
        long before = ZonedDateTime.of(2026, 8, 17, 3, 59, 59, 0,
                ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        assertEquals("2026-08-16", clock.day(before));
        assertEquals("2026-08-10", clock.week(before));
        assertEquals("2026-08-17", clock.day(before + 1_000L));
        assertEquals("2026-08-17", clock.week(before + 1_000L));
    }

    @Test void recentVisitorsAreUniqueButWeeklyVotesAccumulate() {
        UUID city = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CitySocialState state = CitySocialState.empty(city, temporary.resolve(city + ".yml").toFile());
        assertTrue(state.qualify(first, "2026-08-17", "2026-08-17", 1L));
        assertTrue(state.qualify(first, "2026-08-18", "2026-08-17", 2L));
        assertTrue(state.qualify(second, "2026-08-18", "2026-08-17", 3L));
        assertTrue(state.like(first, "2026-08-18", "2026-08-17", 4L));
        assertTrue(state.like(first, "2026-08-24", "2026-08-24", 5L));

        CitySocialState.Metrics metrics = state.metrics(Set.of("2026-08-17", "2026-08-18"));
        assertEquals(2, metrics.visitors());
        assertEquals(1, metrics.likes());
        assertEquals(2L, state.totalLikes());
    }

    @Test void failedAtomicSaveRestoresLikeAndTotal() throws Exception {
        UUID city = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        File file = temporary.resolve(city + ".yml").toFile();
        CitySocialState state = CitySocialState.empty(city, file);
        assertTrue(state.qualify(visitor, "2026-08-17", "2026-08-17", 1L));
        assertTrue(file.delete());
        assertTrue(file.mkdir());

        assertThrows(RuntimeException.class,
                () -> state.like(visitor, "2026-08-17", "2026-08-17", 2L));
        assertEquals(0L, state.totalLikes());
        assertFalse(state.hasLiked(visitor, "2026-08-17"));
    }

    @Test void persistedStateReloadsButFutureSchemaIsRejected() {
        UUID city = UUID.randomUUID();
        File file = temporary.resolve(city + ".yml").toFile();
        CitySocialState state = CitySocialState.empty(city, file);
        assertTrue(state.qualify(UUID.randomUUID(), "2026-08-17", "2026-08-17", 1L));
        assertEquals(0L, CitySocialState.load(city, file).totalLikes());

        YamlConfiguration yaml = YamlFiles.load(file);
        yaml.set("schema_version", 2);
        YamlFiles.save(yaml, file);
        assertThrows(IllegalArgumentException.class, () -> CitySocialState.load(city, file));
    }
}
