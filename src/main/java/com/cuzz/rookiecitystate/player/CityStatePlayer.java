package com.cuzz.rookiecitystate.player;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.gui.GUI;
import com.cuzz.rookiecitystate.gui.BasePageableGUI;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.request.Receiver;
import com.cuzz.rookiecitystate.request.Sender;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class CityStatePlayer implements Sender, Receiver {
    private final File file;
    private YamlConfiguration yaml;
    private String name;
    private UUID uuid;
    private GUI usingGUI;
    private CityStatePlayerMessageBox messageBox;

    CityStatePlayer(File file) {
        this.file = file;

        if (!file.exists()) {
            throw new RuntimeException("玩家不存在");
        }

        load();
    }

    /**
     * 初始化
     * @return
     */
    public void load() {
        this.yaml = YamlFiles.load(file);
        this.uuid = UUID.fromString(yaml.getString("uuid"));
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        this.name = Optional.ofNullable(yaml.getString("known_name"))
                .orElseGet(() -> Optional.ofNullable(offlinePlayer.getName()).orElse(uuid.toString()));

        if (!yaml.contains("message_box")) {
            yaml.createSection("message_box");
        }

        this.messageBox = new CityStatePlayerMessageBox(this);
    }

    public void setKnownName(@NotNull String knownName) {
        String previous = yaml.getString("known_name");
        yaml.set("known_name", knownName);
        try {
            save();
        } catch (RuntimeException exception) {
            yaml.set("known_name", previous);
            throw exception;
        }
        this.name = knownName;
    }

    public String getName() {
        return name;
    }

    public void closeInventory() {
        Player player = getBukkitPlayer();
        if (player != null) player.closeInventory();
    }

    public boolean isUsingGUI() {
        return usingGUI != null;
    }

    public GUI getUsingGUI() {
        return usingGUI;
    }

    public void setUsingGUI(GUI usingGUI) {
        this.usingGUI = usingGUI;
    }

    public CityState getCityState() {
        return RookieCityState.inst().getCityStateManager().getCityStateByMember(uuid);
    }

    public Player getBukkitPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public boolean isOnline() {
        Player tmp = getBukkitPlayer();

        return tmp != null && tmp.isOnline();
    }

    public boolean isInCityState() {
        return getCityState() != null;
    }

    /**
     * 深度更新GUI
     */
    public void updateGUI(GUI.Type... guiTypes) {
        if (usingGUI == null) return;
        GUI.Type usingGUIType = usingGUI.getGUIType();

        for (GUI.Type guiType : guiTypes) {
            if (usingGUIType == guiType) {
                usingGUI.reopen();
            }

            GUI lastGUI = usingGUI;

            while ((lastGUI = lastGUI.getLastGUI()) != null) {
                if (lastGUI.canUse() && lastGUI instanceof BasePageableGUI) {
                    ((BasePageableGUI) lastGUI).update();
                }
            }
        }
    }

    public UUID getUuid() {
        return uuid;
    }

    public OfflinePlayer getOfflineBukkitPlayer() {
        return Bukkit.getOfflinePlayer(getUuid());
    }

    public void save() {
        YamlFiles.save(yaml, file);
    }

    public YamlConfiguration getYaml() {
        return yaml;
    }

    public CityStatePlayerMessageBox getMessageBox() {
        return messageBox;
    }
}
