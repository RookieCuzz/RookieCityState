package com.cuzz.rookiecitystate.world;

import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Safely installs the immutable world archive shipped with RookieCityState. */
public final class BundledCityTemplateInstaller {
    public static final String RESOURCE = "world_templates/citystate_template.zip";
    public static final String SHA256 = "C0A4B402A71DB61067A04E60291BC3DA22EB70E225C9E453FC2D9E93F55823CF";
    public static final String MARKER = ".rookiecitystate-bundled-template.yml";
    private static final long MAX_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L;

    private BundledCityTemplateInstaller() { }

    /**
     * @return true when the target was installed from this exact bundled archive, including an earlier run.
     */
    public static boolean installIfMissing(JavaPlugin plugin, Path worldRoot, String worldName) throws IOException {
        Path root = worldRoot.toAbsolutePath().normalize();
        Path target = root.resolve(worldName).normalize();
        if (!target.getParent().equals(root)) throw new IllegalArgumentException("模板世界名会导致路径越界");
        if (Files.exists(target)) return markerMatches(target);

        byte[] archive;
        try (InputStream input = plugin.getResource(RESOURCE)) {
            if (input == null) throw new IOException("插件缺少内置城邦模板: " + RESOURCE);
            archive = input.readAllBytes();
        }
        String actual = sha256(archive);
        if (!SHA256.equals(actual)) throw new IOException("内置城邦模板 SHA-256 校验失败: " + actual);

        Path staging = root.resolve("." + worldName + ".installing").normalize();
        if (!staging.getParent().equals(root)) throw new IOException("模板暂存路径越界");
        deleteTree(staging);
        Files.createDirectories(staging);
        try {
            extract(archive, staging);
            writeMarker(staging);
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staging, target);
            }
            return true;
        } catch (Throwable failure) {
            try { deleteTree(staging); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            if (failure instanceof IOException io) throw io;
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new IOException("安装内置城邦模板失败", failure);
        }
    }

    public static boolean markerMatches(Path worldFolder) {
        Path marker = worldFolder.resolve(MARKER);
        if (!Files.isRegularFile(marker)) return false;
        try {
            YamlConfiguration yaml = YamlFiles.load(marker.toFile());
            return SHA256.equalsIgnoreCase(yaml.getString("archive_sha256", ""));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void extract(byte[] archive, Path target) throws IOException {
        Set<String> files = new HashSet<>();
        long total = 0L;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory()) continue;
                if (!name.equals("level.dat") && !name.matches("region/r\\.-?[0-9]+\\.-?[0-9]+\\.mca")) {
                    throw new IOException("内置模板包含未许可文件: " + name);
                }
                if (!files.add(name)) throw new IOException("内置模板包含重复文件: " + name);
                Path output = target.resolve(name).normalize();
                if (!output.startsWith(target)) throw new IOException("内置模板 ZIP 路径越界: " + name);
                Files.createDirectories(output.getParent());
                long written = Files.copy(zip, output);
                total = Math.addExact(total, written);
                if (total > MAX_UNCOMPRESSED_BYTES) throw new IOException("内置模板解压后超过安全上限");
            }
        }
        if (!files.contains("level.dat") || files.stream().noneMatch(name -> name.startsWith("region/"))) {
            throw new IOException("内置模板缺少 level.dat 或区域文件");
        }
    }

    private static void writeMarker(Path folder) {
        YamlConfiguration marker = new YamlConfiguration();
        marker.set("schema_version", 1);
        marker.set("archive_sha256", SHA256);
        marker.set("source_format", "JAVA_1_21_4");
        marker.set("source_level_name", "hubcastle_to_1.21.9");
        marker.set("installed_at", System.currentTimeMillis());
        YamlFiles.save(marker, folder.resolve(MARKER).toFile());
    }

    private static String sha256(byte[] data) throws IOException {
        try { return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (NoSuchAlgorithmException impossible) { throw new IOException("JVM 不支持 SHA-256", impossible); }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
