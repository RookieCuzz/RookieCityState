package com.cuzz.rookiecitystate.logger;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.internal.text.TextService;
import com.cuzz.rookiecitystate.util.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class PluginLogger {
    private static LoggerLevel level = LoggerLevel.INFO;
    private static String openDate;
    private static BufferedWriter writer;

    private PluginLogger() {
    }

    public static synchronized void init() {
        try {
            Files.createDirectories(RookieCityState.inst().getDataFolder().toPath().resolve("logs"));
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建日志目录", exception);
        }
    }

    public static LoggerLevel getLevel() { return level; }
    public static void setLevel(@NotNull LoggerLevel newLevel) { level = newLevel; }
    public static void debug(@NotNull String message) { log(LoggerLevel.DEBUG, message); }
    public static void debug(@NotNull String message, @NotNull Object... args) { debug(String.format(message, args)); }
    public static void info(@NotNull String message) { log(LoggerLevel.INFO, message); }
    public static void warning(@NotNull String message) { log(LoggerLevel.WARNING, message); }
    public static void error(@NotNull String message) { error(message, null); }

    public static void error(@NotNull String message, @Nullable RuntimeException exception) {
        log(LoggerLevel.ERROR, message);
        if (exception != null) {
            StringWriter buffer = new StringWriter();
            exception.printStackTrace(new PrintWriter(buffer));
            log(LoggerLevel.ERROR, buffer.toString());
        }
    }

    private static synchronized void log(LoggerLevel messageLevel, String message) {
        if (messageLevel.getLevel() < level.getLevel()) return;
        Util.sendConsoleMsg("§" + messageLevel.color + "[" + messageLevel.name() + "] " + message);
        write("[" + TextService.formatTime(System.currentTimeMillis()) + "] [" + messageLevel.name() + "] " + message);
    }

    private static void write(String message) {
        try {
            String date = TextService.formatDate(System.currentTimeMillis());
            if (!date.equals(openDate)) {
                closeWriters();
                Path path = RookieCityState.inst().getDataFolder().toPath().resolve("logs").resolve(date + ".log");
                writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                openDate = date;
            }
            writer.write(message);
            writer.newLine();
        } catch (IOException exception) {
            throw new IllegalStateException("无法写入日志", exception);
        }
    }

    public static synchronized boolean isWriterEnabled() { return writer != null; }

    public static synchronized void flushWriter() {
        if (writer == null) return;
        try { writer.flush(); } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    public static synchronized void closeWriters() {
        if (writer == null) return;
        try { writer.close(); } catch (IOException exception) { throw new IllegalStateException(exception); }
        writer = null;
        openDate = null;
    }
}
