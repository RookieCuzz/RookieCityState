package com.cuzz.rookiecitystate.wishtree;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WishTreeCoreTest {
    @TempDir Path temporary;

    @Test
    void shanghaiDailyAndWeeklyCyclesResetAtFour() {
        WishTreeClock clock = new WishTreeClock(ZoneId.of("Asia/Shanghai"), 4);
        long before = ZonedDateTime.of(2026, 8, 17, 3, 59, 0, 0,
                ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        long after = ZonedDateTime.of(2026, 8, 17, 4, 0, 0, 0,
                ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        assertEquals("2026-08-16", clock.day(before));
        assertEquals("2026-08-17", clock.day(after));
        assertEquals("2026-08-10", clock.week(before));
        assertEquals("2026-08-17", clock.week(after));
    }

    @Test
    void duplicateWaterIsIdempotent() {
        WishTreeState tree = state();
        UUID player = UUID.randomUUID();
        assertTrue(tree.water(player, "2026-08-17", "2026-08-17").changed());
        assertFalse(tree.water(player, "2026-08-17", "2026-08-17").changed());
        assertEquals(1, tree.getWeeklyGrowth());
        assertEquals(1, tree.getWaterCount(player));
    }

    @Test
    void dynamicThresholdNeverRelocksAnUnlockedMilestone() {
        WishTreeState tree = state();
        tree.water(UUID.randomUUID(), "d1", "week");
        assertEquals(Set.of(25), tree.getUnlockedMilestones());
        assertEquals(4, tree.getWeeklyTarget());

        tree.water(UUID.randomUUID(), "d1", "week");
        tree.water(UUID.randomUUID(), "d1", "week");
        tree.water(UUID.randomUUID(), "d1", "week");
        assertEquals(16, tree.getWeeklyTarget());
        assertTrue(tree.getUnlockedMilestones().contains(25));
    }

    @Test
    void fourMilestonesProduceOneLevelAndVisualCommitControlsLogicalLevel() {
        WishTreeState tree = state();
        UUID player = UUID.randomUUID();
        for (int day = 1; day <= 4; day++) tree.water(player, "day-" + day, "week");
        assertEquals(4, tree.getExperience());
        assertEquals(2, tree.getPendingLevel());
        assertEquals(1, tree.getLevel(), "logical level must wait for the FAWE commit");
        tree.completeVisualUpgrade(2);
        assertEquals(2, tree.getLevel());
        assertEquals(2, tree.getVisualLevel());
    }

    @Test
    void weeklyResetPreservesPermanentExperience() {
        WishTreeState tree = state();
        UUID player = UUID.randomUUID();
        tree.water(player, "day-1", "week-a");
        int experience = tree.getExperience();
        tree.ensureWeek("week-b");
        assertEquals(0, tree.getWeeklyGrowth());
        assertTrue(tree.getUnlockedMilestones().isEmpty());
        assertEquals(experience, tree.getExperience());
    }

    @Test
    void leavingCityInvalidatesCurrentWeeklyContribution() {
        WishTreeState tree = state();
        UUID leaving = UUID.randomUUID();
        tree.water(leaving, "day-1", "week");
        tree.water(leaving, "day-2", "week");
        tree.water(UUID.randomUUID(), "day-2", "week");
        tree.removeParticipant(leaving);
        assertEquals(1, tree.getWeeklyGrowth());
        assertEquals(1, tree.getParticipantCount());
        assertEquals(0, tree.getWaterCount(leaving));
        assertTrue(tree.getUnlockedMilestones().contains(25), "already unlocked tiers stay unlocked");
    }

    @Test
    void bundledRewardCatalogHasBothPityQualitiesAndAllWeeklyTiers() throws Exception {
        YamlConfiguration yaml;
        try (InputStreamReader reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("resources/wish_tree_rewards.yml"),
                StandardCharsets.UTF_8)) {
            yaml = YamlConfiguration.loadConfiguration(reader);
        }
        WishRewardCatalog catalog = WishRewardCatalog.load(yaml);
        assertNull(catalog.get(null), "an unset wish target must be a valid state");
        assertEquals(30, catalog.pityLimit(WishQuality.RARE));
        assertEquals(80, catalog.pityLimit(WishQuality.EPIC));
        assertEquals(0.025D, catalog.earlyChance(WishQuality.RARE));
        assertTrue(catalog.targets(1).stream().anyMatch(reward -> reward.quality() == WishQuality.RARE));
        assertTrue(catalog.targets(5).stream().anyMatch(reward -> reward.quality() == WishQuality.EPIC));
        for (int milestone : WishTreeState.MILESTONES) assertNotNull(catalog.weekly(milestone));
    }

    private WishTreeState state() {
        UUID city = UUID.randomUUID();
        return new WishTreeState(new File(temporary.toFile(), city + ".yml"), city, 1);
    }
}
