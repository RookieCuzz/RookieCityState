package com.cuzz.rookiecitystate.internal.config;

import org.bukkit.configuration.ConfigurationSection;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class SettingsLoader {
    private record Assignment(Field field, Object value) { }

    private SettingsLoader() {
    }

    public static void load(ConfigurationSection yaml, Class<?> settingsClass) {
        List<Assignment> assignments = new ArrayList<>();
        for (Field field : settingsClass.getDeclaredFields()) {
            Config config = field.getAnnotation(Config.class);
            if (config == null || !Modifier.isStatic(field.getModifiers())) continue;
            String path = config.path();
            Object raw = yaml.get(path);
            if (raw == null && field.isAnnotationPresent(NotNull.class)) {
                throw new IllegalArgumentException("必填配置缺失: " + path);
            }
            Object value = convert(raw, field.getType(), path);
            if (value instanceof Double number && !Double.isFinite(number)) {
                throw new IllegalArgumentException("配置必须是有限数: " + path);
            }
            Min min = field.getAnnotation(Min.class);
            if (min != null && value instanceof Number number && number.doubleValue() < min.value()) {
                throw new IllegalArgumentException("配置 " + path + " 不能小于 " + min.value());
            }
            assignments.add(new Assignment(field, value));
        }
        for (Assignment assignment : assignments) {
            try {
                assignment.field().setAccessible(true);
                assignment.field().set(null, assignment.value());
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("无法设置配置字段: " + assignment.field().getName(), exception);
            }
        }
    }

    private static Object convert(Object raw, Class<?> type, String path) {
        if (raw == null) {
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == short.class) return (short) 0;
            if (type == double.class) return 0D;
            return null;
        }
        try {
            if (type == String.class) return String.valueOf(raw);
            if (type == boolean.class || type == Boolean.class) {
                if (raw instanceof Boolean value) return value;
                String text = String.valueOf(raw);
                if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) throw new IllegalArgumentException();
                return Boolean.parseBoolean(text);
            }
            if (type == int.class || type == Integer.class) return raw instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(raw));
            if (type == short.class || type == Short.class) return raw instanceof Number n ? n.shortValue() : Short.parseShort(String.valueOf(raw));
            if (type == double.class || type == Double.class) return raw instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(raw));
            if (List.class.isAssignableFrom(type)) {
                if (!(raw instanceof List<?> list)) throw new IllegalArgumentException();
                List<String> result = new ArrayList<>(list.size());
                for (Object value : list) result.add(String.valueOf(value));
                return List.copyOf(result);
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("配置类型错误: " + path, exception);
        }
        throw new IllegalArgumentException("不支持的配置类型 " + type.getName() + ": " + path);
    }
}
