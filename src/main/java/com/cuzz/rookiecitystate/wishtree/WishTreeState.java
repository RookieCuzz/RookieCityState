package com.cuzz.rookiecitystate.wishtree;

import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WishTreeState {
    public static final List<Integer> MILESTONES = List.of(25, 50, 75, 100);
    private static final int[] LEVEL_XP = {0, 0, 4, 12, 24, 40};

    private final File file;
    private final UUID cityStateId;
    private final YamlConfiguration yaml;

    WishTreeState(File file, UUID cityStateId, int templateRevision) {
        this.file = file;
        this.cityStateId = cityStateId;
        this.yaml = YamlFiles.load(file);
        if (!yaml.contains("schema_version")) {
            yaml.set("schema_version", 1);
            yaml.set("city_state_uuid", cityStateId.toString());
            yaml.set("template_revision", templateRevision);
            yaml.set("level", 1);
            yaml.set("experience", 0);
            yaml.set("visual.level", 1);
            yaml.set("visual.state", "READY");
            save();
        }
        if (!cityStateId.toString().equals(yaml.getString("city_state_uuid"))) {
            throw new IllegalArgumentException("许愿树文件城邦 UUID 不匹配: " + file.getName());
        }
        if (yaml.getInt("schema_version") != 1) throw new IllegalArgumentException("不支持的许愿树 schema_version");
    }

    public synchronized WeekMutation water(UUID playerId, String day, String week) {
        ensureWeek(week);
        String root = "weekly.participants." + playerId;
        if (day.equals(yaml.getString(root + ".last_water_day"))) {
            return WeekMutation.already(getWeeklyGrowth(), getWeeklyTarget(), getUnlockedMilestones(), getLevel());
        }
        yaml.set(root + ".last_water_day", day);
        yaml.set(root + ".water_count", yaml.getInt(root + ".water_count") + 1);
        yaml.set("weekly.growth", getWeeklyGrowth() + 1);
        int target = getWeeklyTarget();
        Set<Integer> unlocked = getUnlockedMilestones();
        Set<Integer> newly = new LinkedHashSet<>();
        for (int milestone : MILESTONES) {
            int required = Math.max(1, (int) Math.ceil(target * (milestone / 100D)));
            if (getWeeklyGrowth() >= required && unlocked.add(milestone)) newly.add(milestone);
        }
        if (!newly.isEmpty()) {
            yaml.set("weekly.unlocked", unlocked.stream().toList());
            yaml.set("experience", getExperience() + newly.size());
        }
        int desired = levelForExperience(getExperience());
        if (desired > getLevel()) yaml.set("visual.pending_level", desired);
        save();
        return WeekMutation.ok(getWeeklyGrowth(), target, newly, desired);
    }

    public synchronized void ensureWeek(String week) {
        if (week.equals(yaml.getString("weekly.cycle"))) return;
        yaml.set("weekly", null);
        yaml.set("weekly.cycle", week);
        yaml.set("weekly.growth", 0);
        yaml.set("weekly.unlocked", List.of());
        save();
    }

    public synchronized void resetWeek(String week) {
        yaml.set("weekly", null);
        yaml.set("weekly.cycle", week);
        yaml.set("weekly.growth", 0);
        yaml.set("weekly.unlocked", List.of());
        save();
    }

    public synchronized int getWaterCount(UUID playerId) {
        return yaml.getInt("weekly.participants." + playerId + ".water_count");
    }

    public synchronized boolean wateredOn(UUID playerId, String day) {
        return day.equals(yaml.getString("weekly.participants." + playerId + ".last_water_day"));
    }

    public synchronized void removeParticipant(UUID playerId) {
        String path = "weekly.participants." + playerId;
        if (!yaml.contains(path)) return;
        int contribution = yaml.getInt(path + ".water_count");
        yaml.set(path, null);
        yaml.set("weekly.growth", Math.max(0, getWeeklyGrowth() - contribution));
        save();
    }

    public synchronized int getParticipantCount() {
        ConfigurationSection participants = yaml.getConfigurationSection("weekly.participants");
        return participants == null ? 0 : participants.getKeys(false).size();
    }

    public synchronized int getWeeklyTarget() { return Math.max(4, getParticipantCount() * 4); }
    public synchronized int getWeeklyGrowth() { return yaml.getInt("weekly.growth"); }
    public synchronized String getWeek() { return yaml.getString("weekly.cycle", ""); }
    public synchronized int getLevel() { return yaml.getInt("level", 1); }
    public synchronized int getExperience() { return yaml.getInt("experience"); }
    public synchronized int getVisualLevel() { return yaml.getInt("visual.level", 1); }
    public synchronized String getVisualState() { return yaml.getString("visual.state", "READY"); }
    public synchronized String getLastError() { return yaml.getString("visual.last_error"); }
    public synchronized int getPendingLevel() { return yaml.getInt("visual.pending_level", getLevel()); }
    public synchronized int getTemplateRevision() { return yaml.getInt("template_revision", 1); }

    public synchronized Set<Integer> getUnlockedMilestones() {
        return new LinkedHashSet<>(yaml.getIntegerList("weekly.unlocked"));
    }

    public synchronized void beginVisualUpgrade(int targetLevel) {
        if (targetLevel < 1 || targetLevel > 5) throw new IllegalArgumentException("树等级必须为 1-5");
        yaml.set("visual.state", "UPGRADING");
        yaml.set("visual.pending_level", targetLevel);
        save();
    }

    public synchronized void completeVisualUpgrade(int level) {
        yaml.set("level", level);
        yaml.set("experience", Math.max(getExperience(), experienceForLevel(level)));
        yaml.set("visual.level", level);
        yaml.set("visual.pending_level", null);
        yaml.set("visual.state", "READY");
        yaml.set("visual.last_error", null);
        save();
    }

    public synchronized void failVisualUpgrade(String error) {
        yaml.set("visual.state", "ERROR");
        yaml.set("visual.last_error", error);
        save();
    }

    public synchronized void resetLevel(int level) {
        yaml.set("experience", experienceForLevel(level));
        yaml.set("visual.pending_level", level);
        save();
    }

    public synchronized boolean cityRewardPaid(int milestone) {
        return yaml.getBoolean("weekly.city_rewards." + milestone + ".paid");
    }

    public synchronized void markCityRewardPaid(int milestone) {
        yaml.set("weekly.city_rewards." + milestone + ".paid", true);
        save();
    }

    public synchronized YamlConfiguration yaml() { return yaml; }
    public synchronized void save() { YamlFiles.save(yaml, file); }
    public File file() { return file; }
    public UUID cityStateId() { return cityStateId; }

    public static int levelForExperience(int experience) {
        int level = 1;
        for (int candidate = 2; candidate <= 5; candidate++) if (experience >= LEVEL_XP[candidate]) level = candidate;
        return level;
    }

    public static int experienceForLevel(int level) {
        if (level < 1 || level > 5) throw new IllegalArgumentException("树等级必须为 1-5");
        return LEVEL_XP[level];
    }

    public record WeekMutation(boolean changed, int growth, int target, Set<Integer> newlyUnlocked, int desiredLevel) {
        static WeekMutation already(int growth, int target, Set<Integer> unlocked, int level) {
            return new WeekMutation(false, growth, target, Set.of(), level);
        }
        static WeekMutation ok(int growth, int target, Set<Integer> unlocked, int desiredLevel) {
            return new WeekMutation(true, growth, target, Set.copyOf(unlocked), desiredLevel);
        }
    }
}
