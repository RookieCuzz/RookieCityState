package com.cuzz.rookiecitystate.world;

import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

final class BundledCityTemplateAssetTest {
    @Test void bundledWorldIsTheInspectedJava1214Archive() throws Exception {
        byte[] archive;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                BundledCityTemplateInstaller.RESOURCE)) {
            assertNotNull(input, "内置城邦模板未进入测试资源");
            archive = input.readAllBytes();
        }
        String hash = HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256").digest(archive));
        assertEquals(BundledCityTemplateInstaller.SHA256, hash);

        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) if (!entry.isDirectory()) entries.add(entry.getName());
        }
        assertEquals(Set.of("level.dat", "region/r.-1.-1.mca", "region/r.-1.0.mca",
                "region/r.0.-1.mca", "region/r.0.0.mca"), entries);
        assertTrue(entries.stream().noneMatch(name -> name.equals("uid.dat") || name.equals("session.lock")
                || name.startsWith("playerdata/") || name.startsWith("DIM")));

        YamlConfiguration manifest = YamlFiles.load(
                new File("src/main/resources/world_templates/citystate_template.yml"));
        assertEquals("JAVA_1_21_4", manifest.getString("source_format"));
        assertEquals(101, manifest.getInt("spawn.y"));
        assertEquals(512, manifest.getInt("world_border_after_install"));
    }

    @Test void bundledMapDefaultsDoNotPasteThePlaceholderHallUnderground() {
        YamlConfiguration settings = YamlFiles.load(new File("src/main/resources/resources/settings.yml"));
        YamlConfiguration guardian = YamlFiles.load(new File("src/main/resources/resources/guardian_beast.yml"));
        assertTrue(settings.getBoolean("city_state.world.bundled_template.enabled"));
        assertFalse(settings.getBoolean("city_state.wish_tree.schematics.main.enabled"));
        assertEquals(101.0D, settings.getDouble("city_state.wish_tree.schematics.main.spawn.y"));
        assertEquals(100, settings.getInt("city_state.wish_tree.schematics.tree.origin.y"));
        assertEquals(-8.5D, guardian.getDouble("visual.anchor.x"));
        assertEquals(101.0D, guardian.getDouble("visual.anchor.y"));
        assertEquals(4.5D, guardian.getDouble("visual.anchor.z"));
    }
}
