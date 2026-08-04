package com.cuzz.rookiecitystate.player;

import com.cuzz.rookiecitystate.messagebox.YamlMessageBox;

public class CityStatePlayerMessageBox extends YamlMessageBox {
    private CityStatePlayer cityStatePlayer;

    public CityStatePlayerMessageBox(CityStatePlayer cityStatePlayer) {
        super(cityStatePlayer.getYaml().getConfigurationSection("message_box"));

        this.cityStatePlayer = cityStatePlayer;
    }

    public CityStatePlayer getCityStatePlayer() {
        return cityStatePlayer;
    }

    @Override
    public void save() {
        cityStatePlayer.save();
    }
}
