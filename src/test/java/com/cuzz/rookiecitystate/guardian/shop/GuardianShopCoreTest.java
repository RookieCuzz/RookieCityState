package com.cuzz.rookiecitystate.guardian.shop;

import com.cuzz.rookiecitystate.guardian.GuardianForm;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

final class GuardianShopCoreTest {
    private GuardianShopConfig config() {
        return GuardianShopConfig.load(YamlFiles.load(
                new File("src/main/resources/resources/guardian_shop.yml")));
    }

    @Test void bundledCatalogIsValidAndContainsAllSixKinds() {
        GuardianShopConfig config = config();
        assertEquals(21, config.products().size());
        assertEquals(6, config.rotationSize());
        assertEquals(java.util.Set.of(GuardianShopProductKind.values()),
                config.products().values().stream().map(GuardianShopProduct::kind).collect(java.util.stream.Collectors.toSet()));
        GuardianShopProduct spell = config.product("action_spell");
        assertTrue(spell.animation(GuardianForm.EGG).isEmpty());
        assertEquals("spell_ground", spell.animation(GuardianForm.ADULT).getFirst().animation());
    }

    @Test void weeklyBoundaryIsMondayAtShanghaiFour() {
        GuardianShopClock clock = new GuardianShopClock(ZoneId.of("Asia/Shanghai"), 4);
        long before = ZonedDateTime.of(2026, 8, 17, 3, 59, 59, 0,
                ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        assertEquals("2026-08-10", clock.week(before));
        assertEquals("2026-08-17", clock.week(before + 1_000L));
        assertEquals(ZonedDateTime.of(2026, 8, 24, 4, 0, 0, 0,
                ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(), clock.nextWeekStart(before + 1_000L));
    }

    @Test void weightedRotationIsDeterministicAndWithoutDuplicates() {
        GuardianShopConfig config = config();
        GuardianShopRotation first = GuardianShopRotationStore.generate("2026-08-17", 42L, 1L, config, new Random(42L));
        GuardianShopRotation second = GuardianShopRotationStore.generate("2026-08-17", 42L, 1L, config, new Random(42L));
        assertEquals(first.products().stream().map(GuardianShopProduct::id).toList(),
                second.products().stream().map(GuardianShopProduct::id).toList());
        assertEquals(6, first.products().size());
        assertEquals(6, new HashSet<>(first.products().stream().map(GuardianShopProduct::id).toList()).size());
    }

    @Test void productSnapshotSurvivesCatalogChanges() {
        GuardianShopProduct original = config().product("particle_amethyst");
        YamlConfiguration snapshot = original.snapshot();
        GuardianShopProduct restored = GuardianShopProduct.loadSnapshot(snapshot);
        assertEquals(original.id(), restored.id());
        assertEquals(original.price(), restored.price());
        assertEquals(original.particle(), restored.particle());
        assertEquals(original.displayName(), restored.displayName());
    }
}
