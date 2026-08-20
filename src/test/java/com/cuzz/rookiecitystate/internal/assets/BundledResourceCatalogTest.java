package com.cuzz.rookiecitystate.internal.assets;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class BundledResourceCatalogTest {
    @Test void everyPublishedConfigurationIsPresentAndListedOnce() throws Exception {
        assertUnique(BundledResourceCatalog.CONFIG_FILES);
        assertUnique(BundledResourceCatalog.GUI_FILES);
        assertUnique(BundledResourceCatalog.SHOP_FILES);
        for (String file : BundledResourceCatalog.CONFIG_FILES) assertResource("resources/" + file);
        for (String file : BundledResourceCatalog.GUI_FILES) assertResource("resources/gui/" + file);
        for (String file : BundledResourceCatalog.SHOP_FILES) assertResource("resources/shop/" + file);
        assertEquals(new LinkedHashSet<>(BundledResourceCatalog.CONFIG_FILES), yamlNames(Path.of("src/main/resources/resources")));
        assertEquals(new LinkedHashSet<>(BundledResourceCatalog.GUI_FILES), yamlNames(Path.of("src/main/resources/resources/gui")));
        assertEquals(new LinkedHashSet<>(BundledResourceCatalog.SHOP_FILES), yamlNames(Path.of("src/main/resources/resources/shop")));
    }

    private void assertUnique(List<String> files) {
        assertEquals(files.size(), new LinkedHashSet<>(files).size(), "资源清单包含重复文件");
        assertTrue(files.stream().allMatch(file -> file.endsWith(".yml")));
    }

    private void assertResource(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            assertTrue(input.readAllBytes().length > 0, path);
        }
    }

    private Set<String> yamlNames(Path directory) throws Exception {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".yml"))
                    .sorted()
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
