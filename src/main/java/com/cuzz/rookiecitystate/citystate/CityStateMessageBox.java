package com.cuzz.rookiecitystate.citystate;

import com.cuzz.rookiecitystate.messagebox.YamlMessageBox;
import org.jetbrains.annotations.NotNull;

public class CityStateMessageBox extends YamlMessageBox {
    private CityState cityState;

    public CityStateMessageBox(@NotNull CityState cityState) {
        super(cityState.getYaml().getConfigurationSection("message_box"));

        this.cityState = cityState;
    }

    public CityState getCityState() {
        return cityState;
    }

    @Override
    public void save() {
        cityState.save();
    }
}
