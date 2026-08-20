package com.cuzz.rookiecitystate.guardian;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class GuardianBeastCoreTest {
    @TempDir Path temporary;

    @Test void resetBoundaryUsesShanghaiFourOClock() {
        GuardianClock clock = new GuardianClock(ZoneId.of("Asia/Shanghai"), 4);
        long before = ZonedDateTime.of(2026, 8, 14, 3, 59, 59, 0, ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        long after = ZonedDateTime.of(2026, 8, 14, 4, 0, 0, 0, ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        assertEquals("2026-08-13", clock.day(before));
        assertEquals("2026-08-14", clock.day(after));
    }

    @Test void levelAndFormFollowFiveThresholds() {
        GuardianBeastConfig config = config(2);
        assertEquals(0, config.level(2));
        assertEquals(GuardianForm.EGG, config.form(2));
        assertEquals(1, config.level(3));
        assertEquals(2, config.level(8));
        assertEquals(GuardianForm.BABY, config.form(17));
        assertEquals(3, config.level(18));
        assertEquals(4, config.level(33));
        assertEquals(5, config.level(53));
        assertEquals(GuardianForm.ADULT, config.form(53));
    }

    @Test void eachDayGetsThreeStableDistinctFavorites() {
        GuardianBeastConfig config = config(2);
        GuardianBeastState state = state();
        state.ensureDay("2026-08-14", config, new Random(7));
        var first = state.favorites();
        assertEquals(3, first.size());
        assertEquals(3, new HashSet<>(first).size());
        state.ensureDay("2026-08-14", config, new Random(99));
        assertEquals(first, state.favorites());
        state.ensureDay("2026-08-15", config, new Random(99));
        assertEquals("2026-08-15", state.day());
        assertEquals(3, new HashSet<>(state.favorites()).size());
    }

    @Test void favoriteMultiplierIsSnapshottedUntilNextDay() {
        GuardianBeastState state = state();
        GuardianBeastConfig original = config(2);
        state.ensureDay("2026-08-14", original, new Random(4));
        Material favorite = state.favorites().getFirst();
        GuardianFood food = original.food(favorite);
        GuardianFeedMutation mutation = state.applyFeed(UUID.randomUUID(), food, config(5).favoriteMultiplier(), 1L);
        assertEquals(food.nourishment() * 2, mutation.nourishment());
        assertEquals(food.contribution() * 2, mutation.contribution());
    }

    @Test void fullDayCompletesExactlyOnceAndLaterFeedsStillContribute() {
        GuardianBeastState state = state();
        GuardianBeastConfig config = config(2);
        state.ensureDay("2026-08-14", config, new Random(1));
        Material material = state.favorites().getFirst();
        GuardianFood food = config.food(material);
        UUID player = UUID.randomUUID();
        int completions = 0;
        while (!state.completedToday()) {
            if (state.applyFeed(player, food, config.favoriteMultiplier(), 2L).dailyCompleted()) completions++;
        }
        assertEquals(1, completions);
        assertEquals(20, state.fullness());
        assertEquals(1, state.completedDays());
        GuardianFeedMutation afterFull = state.applyFeed(player, food, config.favoriteMultiplier(), 3L);
        assertFalse(afterFull.dailyCompleted());
        assertTrue(afterFull.contribution() > 0);
        assertEquals(20, state.fullness());
        assertEquals(1, state.completedDays());
    }

    @Test void speciesIsOneTimeUntilAdministrativeReset() {
        GuardianBeastState state = state();
        UUID owner = UUID.randomUUID();
        state.select(GuardianSpecies.FIRE, owner, 10L);
        assertThrows(IllegalStateException.class, () -> state.select(GuardianSpecies.FROST, owner, 11L));
        state.setCompletedDays(18);
        state.resetSpecies();
        assertNull(state.species());
        assertEquals(0, state.completedDays());
    }

    @Test void persistedStateSurvivesReload() {
        File file = temporary.resolve("persist.yml").toFile();
        UUID city = UUID.randomUUID();
        GuardianBeastState state = new GuardianBeastState(file, city, 1);
        state.select(GuardianSpecies.FOREST, UUID.randomUUID(), 10L);
        state.setCompletedDays(33);
        state.save();
        GuardianBeastState loaded = new GuardianBeastState(file, city, 1);
        assertEquals(GuardianSpecies.FOREST, loaded.species());
        assertEquals(33, loaded.completedDays());
    }

    private GuardianBeastState state() {
        UUID city = UUID.randomUUID();
        return new GuardianBeastState(temporary.resolve(city + ".yml").toFile(), city, 1);
    }

    static GuardianBeastConfig config(int favoriteMultiplier) {
        String yaml = """
                reset: {timezone: Asia/Shanghai, hour: 4}
                daily: {max_feeds: 5, target: 20, favorite_count: 3, favorite_multiplier: %d}
                growth: {completed_day_thresholds: [3, 8, 18, 33, 53]}
                models: {revision: 1, egg: rcs_guardian_egg_r1}
                species:
                  '1': {name: 赤焰龙, icon: BLAZE_POWDER, baby_model: rcs_guardian_1_baby_r1, adult_model: rcs_guardian_1_adult_r1}
                  '2': {name: 森灵龙, icon: OAK_SAPLING, baby_model: rcs_guardian_2_baby_r1, adult_model: rcs_guardian_2_adult_r1}
                  '3': {name: 霜晶龙, icon: BLUE_ICE, baby_model: rcs_guardian_3_baby_r1, adult_model: rcs_guardian_3_adult_r1}
                foods:
                  cod: {material: COD, nourishment: 1, contribution: 1}
                  salmon: {material: SALMON, nourishment: 2, contribution: 2}
                  tropical: {material: TROPICAL_FISH, nourishment: 3, contribution: 3}
                  puffer: {material: PUFFERFISH, nourishment: 4, contribution: 4}
                  cooked_cod: {material: COOKED_COD, nourishment: 2, contribution: 2}
                  cooked_salmon: {material: COOKED_SALMON, nourishment: 3, contribution: 3}
                visual: {anchor: {x: -24.5, y: 65.0, z: 4.5, yaw: 0}}
                """.formatted(favoriteMultiplier);
        YamlConfiguration configuration = new YamlConfiguration();
        try { configuration.loadFromString(yaml); }
        catch (InvalidConfigurationException error) { throw new AssertionError(error); }
        return GuardianBeastConfig.load(configuration);
    }
}
