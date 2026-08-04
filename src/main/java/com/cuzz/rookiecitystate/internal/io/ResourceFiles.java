package com.cuzz.rookiecitystate.internal.io;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class ResourceFiles {
    private ResourceFiles() {
    }

    public static void copy(JavaPlugin plugin, String resourcePath, File destination, boolean replace) {
        if (destination.exists() && !replace) {
            return;
        }
        File parent = destination.getParentFile();
        try {
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            try (InputStream input = plugin.getResource(resourcePath)) {
                if (input == null) {
                    throw new IllegalArgumentException("插件资源不存在: " + resourcePath);
                }
                if (replace) {
                    Files.copy(input, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.copy(input, destination.toPath());
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法复制资源到 " + destination.getAbsolutePath(), exception);
        }
    }

    public static String baseName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
