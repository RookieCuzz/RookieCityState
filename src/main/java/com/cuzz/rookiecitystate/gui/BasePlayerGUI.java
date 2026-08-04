package com.cuzz.rookiecitystate.gui;

import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 一个GUI的实现类
 */
public abstract class BasePlayerGUI implements GUI {
    protected final GUI lastGUI;
    protected final GUI.Type type;
    protected final CityStatePlayer cityStatePlayer;

    protected BasePlayerGUI(@Nullable GUI lastGUI, @NotNull GUI.Type guiType, @NotNull CityStatePlayer cityStatePlayer) {
        this.lastGUI = lastGUI;
        this.type = guiType;
        this.cityStatePlayer = cityStatePlayer;

        PluginLogger.debug("开始创建 GUI 类 " + getClass().getName() + ".");
    }

    @Override
    public GUI getLastGUI() {
        return lastGUI;
    }

    @Override
    public CityStatePlayer getCityStatePlayer() {
        return cityStatePlayer;
    }

    @Override
    public GUI.Type getGUIType() {
        return type;
    }
}
