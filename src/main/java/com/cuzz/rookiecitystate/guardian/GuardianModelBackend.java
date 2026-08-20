package com.cuzz.rookiecitystate.guardian;

import org.bukkit.entity.ArmorStand;

public interface GuardianModelBackend {
    boolean isRegistered(String modelId);
    ModelHandle create(ArmorStand base, String modelId);
    void playAnimation(ModelHandle handle, String animation, double lerpIn, double lerpOut, double speed, boolean force);
    void destroy(ModelHandle handle);

    interface ModelHandle { }
}
