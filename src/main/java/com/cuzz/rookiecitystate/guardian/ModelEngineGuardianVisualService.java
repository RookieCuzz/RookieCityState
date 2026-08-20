package com.cuzz.rookiecitystate.guardian;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class ModelEngineGuardianVisualService implements GuardianVisualService {
    public static final String ROLE_BASE = "base";
    public static final String ROLE_INTERACTION = "interaction";
    public static final String ROLE_TEXT = "text";
    private final RookieCityState plugin;
    private final GuardianBeastService service;
    private final GuardianBlueprintInstaller installer;
    private final GuardianModelBackend modelBackend;
    private final org.bukkit.NamespacedKey cityKey;
    private final org.bukkit.NamespacedKey roleKey;
    private final Map<UUID, VisualHandle> handles = new HashMap<>();
    private final BukkitTask particleTask;

    public ModelEngineGuardianVisualService(RookieCityState plugin, GuardianBeastService service,
                                            GuardianBlueprintInstaller installer, GuardianModelBackend modelBackend) {
        this.plugin = plugin;
        this.service = service;
        this.installer = installer;
        this.modelBackend = modelBackend;
        this.cityKey = new org.bukkit.NamespacedKey(plugin, "guardian_city");
        this.roleKey = new org.bukkit.NamespacedKey(plugin, "guardian_role");
        this.particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickParticles, 40L, 40L);
    }

    @Override public boolean isAvailable() { return installer.currentStatus().ready(); }

    @Override public String unavailableReason() {
        GuardianModelInstallStatus status = installer.currentStatus();
        if (!status.assetsValid()) return status.errors().isEmpty() ? "灵兽蓝图未安装" : String.join("；", status.errors());
        if (!status.modelsRegistered()) return "模型尚未注册，请运行 /meg reload models 并部署生成的资源包";
        return null;
    }

    @Override public CompletionStage<Void> ensureVisual(CityState cityState) {
        return onMain(() -> {
            if (!isAvailable()) throw new IllegalStateException(unavailableReason());
            World world = Bukkit.getWorld(cityState.getWorldName());
            if (world == null) return null;
            GuardianBeastState state = service.state(cityState);
            GuardianBeastConfig config = service.getConfig();
            String modelId = config.model(state.species(), state.completedDays());
            Location expectedAnchor = anchor(world);
            VisualHandle existing = handles.get(cityState.getUuid());
            if (existing != null && existing.valid(world, expectedAnchor) && existing.modelId.equals(modelId)
                    && taggedCount(world, cityState.getUuid()) == 3) {
                updateText(existing.text, state, config);
                return null;
            }
            removeVisual(cityState.getUuid(), world);
            VisualHandle created = spawn(cityState, world, state, config, modelId);
            handles.put(cityState.getUuid(), created);
            state.visualReady(modelId);
            return null;
        });
    }

    @Override public CompletionStage<Void> updateVisual(CityState cityState, GuardianForm previous, GuardianForm next) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Runnable replace = () -> ensureVisual(cityState).whenComplete((ignored, error) -> {
            if (error == null) future.complete(null); else future.completeExceptionally(error);
        });
        if (previous == GuardianForm.EGG && next == GuardianForm.BABY) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                VisualHandle handle = handles.get(cityState.getUuid());
                if (handle != null) {
                    try { modelBackend.playAnimation(handle.model, "eclode", 0.1, 0.1, 1.0, true); }
                    catch (RuntimeException ignored) { }
                }
                Bukkit.getScheduler().runTaskLater(plugin, replace, 62L);
            });
        } else if (previous == GuardianForm.BABY && next == GuardianForm.ADULT) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                World world = Bukkit.getWorld(cityState.getWorldName());
                if (world != null) world.spawnParticle(Particle.END_ROD, anchor(world), 80, 1.1, 1.6, 1.1, 0.03);
                Bukkit.getScheduler().runTaskLater(plugin, replace, 10L);
            });
        } else Bukkit.getScheduler().runTask(plugin, replace);
        return future;
    }

    @Override public CompletionStage<Void> playAction(CityState cityState, List<GuardianAnimationStep> steps) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (steps == null || steps.isEmpty()) throw new IllegalArgumentException("动画序列为空");
                VisualHandle handle = handles.get(cityState.getUuid());
                if (handle == null || !handle.base.isValid()) throw new IllegalStateException("灵兽视觉尚未加载");
                playStep(cityState.getUuid(), handle, List.copyOf(steps), 0, future);
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    private void playStep(UUID cityId, VisualHandle expected, List<GuardianAnimationStep> steps,
                          int index, CompletableFuture<Void> future) {
        if (future.isDone()) return;
        VisualHandle current = handles.get(cityId);
        if (current != expected || !expected.base.isValid()) {
            future.completeExceptionally(new IllegalStateException("灵兽视觉在动作期间失效"));
            return;
        }
        if (index >= steps.size()) {
            try { modelBackend.playAnimation(expected.model, "idle", 0.15, 0.15, 1D, true); }
            catch (RuntimeException ignored) { }
            future.complete(null);
            return;
        }
        GuardianAnimationStep step = steps.get(index);
        try { modelBackend.playAnimation(expected.model, step.animation(), 0.1, 0.1, step.speed(), step.force()); }
        catch (RuntimeException error) { future.completeExceptionally(error); return; }
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> playStep(cityId, expected, steps, index + 1, future), step.durationTicks());
    }

    public UUID cityId(Entity entity) {
        String value = entity.getPersistentDataContainer().get(cityKey, PersistentDataType.STRING);
        if (value == null) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    public boolean isInteraction(Entity entity) {
        return ROLE_INTERACTION.equals(entity.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING));
    }

    @Override public CompletionStage<Void> reconcileLoadedWorlds() {
        if (!isAvailable()) return CompletableFuture.failedFuture(new IllegalStateException(unavailableReason()));
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (CityState city : plugin.getCityStateManager().getCityStates()) {
            if (Bukkit.getWorld(city.getWorldName()) != null) futures.add(ensureVisual(city).toCompletableFuture());
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override public void worldUnloaded(String worldName) {
        CityState city = plugin.getCityWorldService().findCityByWorld(worldName);
        if (city != null) {
            VisualHandle handle = handles.remove(city.getUuid());
            if (handle != null) try { modelBackend.destroy(handle.model); } catch (RuntimeException ignored) { }
        }
    }

    @Override public void shutdown() {
        particleTask.cancel();
        for (VisualHandle handle : new ArrayList<>(handles.values())) destroy(handle);
        handles.clear();
    }

    private void tickParticles() {
        for (Map.Entry<UUID, VisualHandle> entry : new ArrayList<>(handles.entrySet())) {
            CityState city = plugin.getCityStateManager().getCityState(entry.getKey());
            VisualHandle handle = entry.getValue();
            if (city == null || !handle.base.isValid()) continue;
            GuardianBeastState state = service.state(city);
            int level = service.getConfig().level(state.completedDays());
            if (level < 1) continue;
            handle.base.getWorld().spawnParticle(Particle.END_ROD,
                    handle.base.getLocation().add(0, level >= 3 ? 1.3 : 0.8, 0),
                    1 + level * 2, 0.45 + level * 0.08, 0.55 + level * 0.1,
                    0.45 + level * 0.08, 0.006 + level * 0.002);
        }
    }

    private VisualHandle spawn(CityState cityState, World world, GuardianBeastState state,
                               GuardianBeastConfig config, String modelId) {
        if (!modelBackend.isRegistered(modelId)) throw new IllegalStateException("模型未注册: " + modelId);
        Location location = anchor(world);
        ArmorStand base = world.spawn(location, ArmorStand.class, entity -> {
            entity.setInvisible(true); entity.setGravity(false); entity.setSilent(true); entity.setInvulnerable(true);
            entity.setPersistent(false); entity.setMarker(true); tag(entity, cityState.getUuid(), ROLE_BASE);
        });
        GuardianModelBackend.ModelHandle model = null;
        Interaction interaction = null;
        TextDisplay text = null;
        try {
            model = modelBackend.create(base, modelId);
            try { modelBackend.playAnimation(model, "idle", 0.2, 0.2, 1.0, true); }
            catch (RuntimeException ignored) { }

            GuardianForm form = config.form(state.completedDays());
            interaction = world.spawn(location, Interaction.class, entity -> {
                entity.setPersistent(false); entity.setResponsive(true); tag(entity, cityState.getUuid(), ROLE_INTERACTION);
                if (form == GuardianForm.EGG) { entity.setInteractionWidth(1.1f); entity.setInteractionHeight(1.2f); }
                else if (form == GuardianForm.BABY) { entity.setInteractionWidth(1.0f); entity.setInteractionHeight(1.4f); }
                else { entity.setInteractionWidth(1.8f); entity.setInteractionHeight(2.2f); }
            });
            text = world.spawn(location.clone().add(0, form == GuardianForm.ADULT ? 2.7 : 1.9, 0),
                    TextDisplay.class, entity -> {
                        entity.setPersistent(false); entity.setBillboard(Display.Billboard.CENTER);
                        entity.setSeeThrough(true); entity.setShadowed(true); tag(entity, cityState.getUuid(), ROLE_TEXT);
                    });
            updateText(text, state, config);
            return new VisualHandle(world.getUID(), modelId, location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), base, interaction, text, model);
        } catch (RuntimeException error) {
            if (model != null) try { modelBackend.destroy(model); } catch (RuntimeException ignored) { }
            base.remove();
            if (interaction != null) interaction.remove();
            if (text != null) text.remove();
            throw error;
        }
    }

    private void updateText(TextDisplay text, GuardianBeastState state, GuardianBeastConfig config) {
        int level = config.level(state.completedDays());
        NamedTextColor color = switch (level) {
            case 0 -> NamedTextColor.GRAY;
            case 1 -> NamedTextColor.GREEN;
            case 2 -> NamedTextColor.AQUA;
            case 3 -> NamedTextColor.BLUE;
            case 4 -> NamedTextColor.LIGHT_PURPLE;
            default -> NamedTextColor.GOLD;
        };
        String name = state.species() == null ? "公共灵兽蛋" : config.species(state.species()).displayName();
        text.text(Component.text(name + "  Lv." + level, color)
                .append(Component.text("\n今日饱食 " + state.fullness() + "/" + state.target(), NamedTextColor.WHITE)));
    }

    private Location anchor(World world) {
        GuardianBeastConfig config = service.getConfig();
        return new Location(world, config.anchorX(), config.anchorY(), config.anchorZ(), config.anchorYaw(), 0f);
    }

    private void removeVisual(UUID cityId, World world) {
        VisualHandle handle = handles.remove(cityId);
        if (handle != null) destroy(handle);
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            String stored = entity.getPersistentDataContainer().get(cityKey, PersistentDataType.STRING);
            if (cityId.toString().equals(stored)) entity.remove();
        }
    }

    private long taggedCount(World world, UUID cityId) {
        String expected = cityId.toString();
        return world.getEntities().stream().filter(entity -> expected.equals(
                entity.getPersistentDataContainer().get(cityKey, PersistentDataType.STRING))).count();
    }

    private void destroy(VisualHandle handle) {
        try { modelBackend.destroy(handle.model); } catch (RuntimeException ignored) { }
        handle.base.remove(); handle.interaction.remove(); handle.text.remove();
    }

    private void tag(Entity entity, UUID cityId, String role) {
        entity.getPersistentDataContainer().set(cityKey, PersistentDataType.STRING, cityId.toString());
        entity.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, role);
    }

    private CompletionStage<Void> onMain(java.util.concurrent.Callable<Void> callable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Runnable task = () -> { try { callable.call(); future.complete(null); } catch (Throwable error) { future.completeExceptionally(error); } };
        if (Bukkit.isPrimaryThread()) task.run(); else Bukkit.getScheduler().runTask(plugin, task);
        return future;
    }

    private record VisualHandle(UUID worldId, String modelId, double anchorX, double anchorY, double anchorZ,
                                float anchorYaw, ArmorStand base, Interaction interaction,
                                TextDisplay text, GuardianModelBackend.ModelHandle model) {
        boolean valid(World world, Location expected) {
            return world.getUID().equals(worldId) && base.isValid() && interaction.isValid() && text.isValid()
                    && Math.abs(anchorX - expected.getX()) < 0.001D
                    && Math.abs(anchorY - expected.getY()) < 0.001D
                    && Math.abs(anchorZ - expected.getZ()) < 0.001D
                    && Math.abs(anchorYaw - expected.getYaw()) < 0.01F;
        }
    }
}
