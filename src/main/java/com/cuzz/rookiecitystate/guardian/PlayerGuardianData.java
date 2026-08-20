package com.cuzz.rookiecitystate.guardian;

import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class PlayerGuardianData {
    private final CityStatePlayer player;

    PlayerGuardianData(CityStatePlayer player) { this.player = player; }

    void ensureDay(String day) {
        if (day.equals(player.getYaml().getString("guardian_beast.daily.cycle"))) return;
        player.getYaml().set("guardian_beast.daily.cycle", day);
        player.getYaml().set("guardian_beast.daily.feeds", 0);
    }

    int feeds() { return Math.max(0, player.getYaml().getInt("guardian_beast.daily.feeds", 0)); }
    long available() { return Math.max(0L, player.getYaml().getLong("guardian_beast.contribution.available", 0L)); }
    long lifetime() { return Math.max(0L, player.getYaml().getLong("guardian_beast.contribution.lifetime", 0L)); }

    void applyFeed(int contribution) {
        player.getYaml().set("guardian_beast.daily.feeds", Math.addExact(feeds(), 1));
        player.getYaml().set("guardian_beast.contribution.available", Math.addExact(available(), contribution));
        player.getYaml().set("guardian_beast.contribution.lifetime", Math.addExact(lifetime(), contribution));
    }

    void grant(long amount) {
        if (amount < 1) throw new IllegalArgumentException("贡献数量必须为正数");
        player.getYaml().set("guardian_beast.contribution.available", Math.addExact(available(), amount));
        player.getYaml().set("guardian_beast.contribution.lifetime", Math.addExact(lifetime(), amount));
    }

    void resetDaily(String day) {
        player.getYaml().set("guardian_beast.daily.cycle", day);
        player.getYaml().set("guardian_beast.daily.feeds", 0);
    }

    String snapshot() { return player.getYaml().saveToString(); }

    void restore(String snapshot) {
        try { player.getYaml().loadFromString(snapshot); }
        catch (InvalidConfigurationException error) { throw new IllegalStateException("无法恢复玩家灵兽事务快照", error); }
    }

    void save() { player.save(); }
}
