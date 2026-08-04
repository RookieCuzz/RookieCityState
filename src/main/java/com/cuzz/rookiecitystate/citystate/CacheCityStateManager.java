package com.cuzz.rookiecitystate.citystate;

import com.cuzz.rookiecitystate.RookieCityState;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CacheCityStateManager {
    private final RookieCityState plugin = RookieCityState.inst();
    private final CityStateManager cityStateManager = plugin.getCityStateManager();
    private List<CityState> sortedCityStates = new ArrayList<>();

    public CacheCityStateManager() {}

    public void startTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateSortedCityStates();
            }
        }.runTaskTimer(plugin, 0L, 20L * 60L);
    }

    public void updateSortedCityStates() {
        sortedCityStates.clear();
        sortedCityStates.addAll(cityStateManager.getSortedCityStates());
    }

    public List<CityState> getSortedCityStates() {
        return new ArrayList<>(sortedCityStates);
    }

    public int getRanking(@NotNull CityState cityState) {
        if (!cityState.isValid()) {
            throw new IllegalArgumentException("城邦无效");
        }

        return sortedCityStates.indexOf(cityState) + 1;
    }

    public void reset() {
        sortedCityStates.clear();
    }
}
