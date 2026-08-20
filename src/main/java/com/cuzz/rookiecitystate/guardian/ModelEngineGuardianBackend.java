package com.cuzz.rookiecitystate.guardian;

import com.cuzz.rookiecitystate.RookieCityState;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.events.ModelRegistrationEvent;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** The only adapter allowed to import ModelEngine API classes. */
public final class ModelEngineGuardianBackend implements GuardianModelBackend, Listener {
    private final RookieCityState plugin;
    private Runnable registrationCallback = () -> { };

    public ModelEngineGuardianBackend(RookieCityState plugin) { this.plugin = plugin; }

    public void setRegistrationCallback(Runnable callback) {
        this.registrationCallback = callback == null ? () -> { } : callback;
    }

    @Override public boolean isRegistered(String modelId) {
        try { return ModelEngineAPI.getBlueprint(modelId) != null; }
        catch (Throwable error) { return false; }
    }

    @Override public ModelHandle create(ArmorStand base, String modelId) {
        if (!isRegistered(modelId)) throw new IllegalStateException("模型未注册: " + modelId);
        ModeledEntity modeled = ModelEngineAPI.createModeledEntity(base);
        try {
            modeled.setSaved(false);
            modeled.setBaseEntityVisible(false);
            ActiveModel model = ModelEngineAPI.createActiveModel(modelId);
            model.setCanHurt(false);
            modeled.addModel(model, true);
            return new Handle(modeled, model);
        } catch (RuntimeException error) {
            try { modeled.destroy(); } catch (RuntimeException ignored) { }
            throw error;
        }
    }

    @Override public void playAnimation(ModelHandle handle, String animation, double lerpIn,
                                        double lerpOut, double speed, boolean force) {
        Handle value = require(handle);
        value.model.getAnimationHandler().playAnimation(animation, lerpIn, lerpOut, speed, force);
    }

    @Override public void destroy(ModelHandle handle) {
        if (handle == null) return;
        require(handle).modeled.destroy();
    }

    @EventHandler
    public void onModelsRegistered(ModelRegistrationEvent event) {
        Bukkit.getScheduler().runTask(plugin, registrationCallback);
    }

    private Handle require(ModelHandle handle) {
        if (!(handle instanceof Handle value)) throw new IllegalArgumentException("不是 ModelEngine 灵兽句柄");
        return value;
    }

    private record Handle(ModeledEntity modeled, ActiveModel model) implements ModelHandle { }
}
