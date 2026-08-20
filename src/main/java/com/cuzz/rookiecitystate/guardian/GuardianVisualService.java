package com.cuzz.rookiecitystate.guardian;

import com.cuzz.rookiecitystate.citystate.CityState;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.List;

public interface GuardianVisualService {
    boolean isAvailable();
    String unavailableReason();
    CompletionStage<Void> ensureVisual(CityState cityState);
    CompletionStage<Void> updateVisual(CityState cityState, GuardianForm previous, GuardianForm next);
    default CompletionStage<Void> playAction(CityState cityState, List<GuardianAnimationStep> steps) {
        return CompletableFuture.failedFuture(new IllegalStateException("当前视觉后端不支持互动动作"));
    }
    default CompletionStage<Void> retry(CityState cityState) { return ensureVisual(cityState); }
    default CompletionStage<Void> reconcileLoadedWorlds() { return CompletableFuture.completedFuture(null); }
    default void worldUnloaded(String worldName) { }
    default void shutdown() { }

    static GuardianVisualService unavailable(String reason) {
        return new GuardianVisualService() {
            @Override public boolean isAvailable() { return false; }
            @Override public String unavailableReason() { return reason; }
            @Override public CompletionStage<Void> ensureVisual(CityState cityState) {
                return CompletableFuture.failedFuture(new IllegalStateException(reason));
            }
            @Override public CompletionStage<Void> updateVisual(CityState cityState, GuardianForm previous, GuardianForm next) {
                return ensureVisual(cityState);
            }
        };
    }
}
