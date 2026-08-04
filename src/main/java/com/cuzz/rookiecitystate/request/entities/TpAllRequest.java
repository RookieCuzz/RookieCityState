package com.cuzz.rookiecitystate.request.entities;

import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.request.BaseRequest;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

public class TpAllRequest extends BaseRequest<CityStateMember, CityStateMember> {
    private Location location;

    public TpAllRequest() {}

    public TpAllRequest(@NotNull CityStateMember sender, @NotNull CityStateMember receiver, @NotNull Location location) {
        super(sender, receiver);

        this.location = location.clone();
    }

    @Override
    public void onSave(@NotNull ConfigurationSection section) {
        super.onSave(section);
        ConfigurationSection locationSection = section.createSection("location");
        YamlFiles.writeLocation(locationSection, location);
    }

    @Override
    public void onLoad(@NotNull ConfigurationSection section) {
        super.onLoad(section);
        ConfigurationSection value = section.getConfigurationSection("location");
        if (value == null) throw new IllegalArgumentException("location 缺失");
        World world = Bukkit.getWorld(value.getString("world", ""));
        if (world == null) {
            location = null;
            return;
        }
        location = new Location(world, value.getDouble("x"), value.getDouble("y"), value.getDouble("z"),
                (float) value.getDouble("yaw"), (float) value.getDouble("pitch"));
    }

    @Override
    public Type getType() {
        return Type.TP_ALL;
    }

    @Override
    public boolean isValid() {
        return getSender() != null && getReceiver() != null && location != null && location.getWorld() != null
                && (System.currentTimeMillis() - getCreationTime()) / 1000L < MainSettings.getCityStateTpAllTimeout()
                && getSender().isValid() && getReceiver().isValid() && getSender().isOnline()
                && getSender().getCityState() == getReceiver().getCityState();
    }

    public Location getLocation() {
        return location == null ? null : location.clone();
    }
}
