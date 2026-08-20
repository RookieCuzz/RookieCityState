package com.cuzz.rookiecitystate.wishtree;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.api.event.WishTreeLevelChangedEvent;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import com.cuzz.rookiecitystate.world.CityWorldProtectionService;
import com.cuzz.rookiecitystate.world.CityWorldService;
import com.cuzz.rookiecitystate.world.operation.CityWorldOperation;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldEditWishTreeVisualService implements WishTreeVisualService {
    public static final String ENTITY_CITY_KEY = "wish_tree_city";
    public static final String ENTITY_KIND_KEY = "wish_tree_kind";
    private final RookieCityState plugin;
    private final CityWorldService worlds;
    @SuppressWarnings("unused") private final CityWorldProtectionService protection;
    private final WishTreeStore store;
    private final Map<UUID, CompletableFuture<Void>> upgrades = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> requestedLevels = new ConcurrentHashMap<>();
    private final NamespacedKey cityKey;
    private final NamespacedKey kindKey;
    private volatile boolean assetsReady;

    public WorldEditWishTreeVisualService(RookieCityState plugin, CityWorldService worlds,
                                          CityWorldProtectionService protection, WishTreeStore store) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.protection = protection;
        this.store = store;
        this.cityKey = new NamespacedKey(plugin, ENTITY_CITY_KEY);
        this.kindKey = new NamespacedKey(plugin, ENTITY_KIND_KEY);
    }

    @Override
    public CompletionStage<Void> prepareAssets() {
        return supplyAsync(() -> {
            Path root = activeRoot();
            createPlaceholderSchematics(root);
            snapshotAssets(root, snapshotRoot());
            assetsReady = true;
            return null;
        }).thenCompose(ignored -> recoverInterruptedOperations());
    }

    private CompletionStage<Void> recoverInterruptedOperations() {
        File folder = new File(plugin.getDataFolder(), "data/operations/wish-tree");
        File[] files = folder.listFiles((directory, name) -> name.endsWith(".yml"));
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        if (files == null) return chain;
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        for (File file : files) {
            chain = chain.thenCompose(ignored -> recoverInterruptedOperation(file).handle((value, error) -> {
                if (error != null) PluginLogger.warning("许愿树视觉操作恢复失败 "
                        + file.getName() + ": " + rootMessage(error));
                return null;
            }));
        }
        return chain;
    }

    private CompletionStage<Void> recoverInterruptedOperation(File file) {
        VisualOperation operation = VisualOperation.load(file);
        if (operation == null) {
            return CompletableFuture.completedFuture(null);
        }
        Path backup = file.toPath().resolveSibling(operation.id() + ".backup.schem");
        if ("COMPLETE".equals(operation.phase())) {
            try {
                Files.deleteIfExists(backup);
                operation.remove();
            } catch (IOException error) { return CompletableFuture.failedFuture(error); }
            return CompletableFuture.completedFuture(null);
        }
        CityState cityState = plugin.getCityStateManager().getCityState(operation.cityStateId());
        if (cityState == null) {
            operation.phase("ORPHANED");
            return CompletableFuture.completedFuture(null);
        }
        WishTreeState state = store.get(cityState);
        if (operation.committed() || "COMMITTED".equals(operation.phase())
                || ("PASTED".equals(operation.phase()) && state.getVisualLevel() == operation.targetLevel())) {
            AtomicBoolean committedLease = new AtomicBoolean(false);
            return worlds.ensureLoaded(cityState).thenCompose(world -> onMain(() -> {
                worlds.acquireMaintenanceLease(cityState.getWorldName());
                committedLease.set(true);
                return new RecoveryContext(world, BukkitAdapter.adapt(world), operation, backup, state, committedLease);
            })).thenCompose(context -> supplyAsync(() -> {
                paste(context.adapted(), treeSchematic(cityState, operation.targetLevel()), treeOrigin());
                return context;
            })).thenCompose(context -> this.<Void>onMain(() -> {
                try {
                    context.world().save();
                    context.state().completeVisualUpgrade(operation.targetLevel());
                    context.operation().phase("STATE_COMMITTED");
                    ensureInteraction(cityState, context.world());
                    return null;
                } finally {
                    if (context.leaseHeld().compareAndSet(true, false)) {
                        worlds.releaseMaintenanceLease(cityState.getWorldName());
                    }
                }
            })).thenCompose(ignored -> this.<Void>supplyAsync(() -> {
                operation.phase("CLEANUP_PENDING");
                Files.deleteIfExists(backup);
                operation.complete();
                operation.remove();
                return null;
            })).exceptionallyCompose(error -> this.<Void>onMain(() -> {
                operation.phase("RECOVERY_REQUIRED");
                state.failVisualUpgrade("已提交的目标结构恢复失败: " + rootMessage(error));
                if (committedLease.compareAndSet(true, false)) worlds.releaseMaintenanceLease(cityState.getWorldName());
                throw new IllegalStateException(rootMessage(error), error);
            }));
        }
        if (!Files.isRegularFile(backup)) {
            state.failVisualUpgrade("服务器中断且升级备份不存在");
            operation.phase("RECOVERY_REQUIRED");
            return CompletableFuture.completedFuture(null);
        }
        AtomicBoolean lease = new AtomicBoolean(false);
        return worlds.ensureLoaded(cityState).thenCompose(world -> onMain(() -> {
            worlds.acquireMaintenanceLease(cityState.getWorldName());
            lease.set(true);
            return new RecoveryContext(world, BukkitAdapter.adapt(world), operation, backup, state, lease);
        })).thenCompose(context -> supplyAsync(() -> {
            context.operation().phase("ROLLING_BACK");
            paste(context.adapted(), context.backup(), treeOrigin());
            return context;
        })).thenCompose(context -> this.<Void>onMain(() -> {
            try {
                context.world().save();
                context.state().failVisualUpgrade("已在启动恢复中回滚未完成的建筑升级");
                context.operation().phase("ROLLED_BACK");
                return null;
            } finally {
                if (context.leaseHeld().compareAndSet(true, false)) {
                    worlds.releaseMaintenanceLease(cityState.getWorldName());
                }
            }
        })).exceptionallyCompose(error -> this.<Void>onMain(() -> {
            operation.phase("ROLLBACK_FAILED");
            state.failVisualUpgrade("启动恢复回滚失败: " + rootMessage(error));
            if (lease.compareAndSet(true, false)) worlds.releaseMaintenanceLease(cityState.getWorldName());
            throw new IllegalStateException(rootMessage(error), error);
        }));
    }

    @Override
    public CompletionStage<Void> pasteInitial(CityState cityState, World world, CityWorldOperation operation) {
        if (!assetsReady) return CompletableFuture.failedFuture(new IllegalStateException("FAWE 结构资源尚未准备完成"));
        com.sk89q.worldedit.world.World adapted = BukkitAdapter.adapt(world);
        return supplyAsync(() -> {
            if (MainSettings.isWishTreeMainSchematicEnabled()) {
                operation.phase("PASTING_CITY");
                paste(adapted, schematic(cityState, MainSettings.getWishTreeMainSchematic()),
                        BlockVector3.at(MainSettings.getWishTreeMainOriginX(), MainSettings.getWishTreeMainOriginY(),
                                MainSettings.getWishTreeMainOriginZ()));
            } else {
                operation.phase("CITY_FROM_TEMPLATE");
            }
            operation.phase("PASTING_TREE");
            paste(adapted, treeSchematic(cityState, 1), treeOrigin());
            store.get(cityState).completeVisualUpgrade(1);
            return null;
        });
    }

    @Override
    public CompletionStage<Void> requestLevel(CityState cityState, int level) {
        if (level < 1 || level > 5) return CompletableFuture.failedFuture(new IllegalArgumentException("树等级必须为 1-5"));
        UUID cityId = cityState.getUuid();
        requestedLevels.put(cityId, level);
        CompletableFuture<Void> created = new CompletableFuture<>();
        CompletableFuture<Void> existing = upgrades.putIfAbsent(cityId, created);
        if (existing != null) return existing;
        created.whenComplete((value, error) -> {
            upgrades.remove(cityId, created);
            requestedLevels.remove(cityId);
        });
        try {
            runUpgradeLoop(cityState).whenComplete((value, error) -> {
                if (error == null) created.complete(null); else created.completeExceptionally(error);
            });
        } catch (Throwable error) {
            created.completeExceptionally(error);
        }
        return created;
    }

    private CompletionStage<Void> runUpgradeLoop(CityState cityState) {
        int requested = requestedLevels.getOrDefault(cityState.getUuid(), store.get(cityState).getPendingLevel());
        return beginUpgrade(cityState, requested).thenCompose(ignored -> {
            int next = requestedLevels.getOrDefault(cityState.getUuid(), requested);
            if (next != store.get(cityState).getVisualLevel()) return runUpgradeLoop(cityState);
            return CompletableFuture.completedFuture(null);
        });
    }

    private CompletionStage<Void> beginUpgrade(CityState cityState, int level) {
        AtomicReference<UpgradeContext> active = new AtomicReference<>();
        return worlds.ensureLoaded(cityState).thenCompose(world -> onMain(() -> {
            worlds.acquireMaintenanceLease(cityState.getWorldName());
            try {
                WishTreeState state = store.get(cityState);
                int previous = state.getVisualLevel();
                state.beginVisualUpgrade(level);
                VisualOperation operation = VisualOperation.create(plugin, cityState, previous, level);
                com.sk89q.worldedit.world.World adapted = BukkitAdapter.adapt(world);
                Path backup = operation.file().toPath().resolveSibling(operation.id() + ".backup.schem");
                UpgradeContext context = new UpgradeContext(world, adapted, state, operation, backup, previous,
                        new AtomicBoolean(true));
                active.set(context);
                return context;
            } catch (Throwable failure) {
                worlds.releaseMaintenanceLease(cityState.getWorldName());
                throw failure;
            }
        })).thenCompose(context -> supplyAsync(() -> {
            backup(context.adapted(), context.backup());
            context.operation().phase("BACKUP_READY");
            paste(context.adapted(), treeSchematic(cityState, level), treeOrigin());
            context.operation().phase("PASTED");
            return context;
        })).thenCompose(context -> onMain(() -> {
            try {
                context.world().save();
                context.state().completeVisualUpgrade(level);
                context.operation().phase("STATE_COMMITTED");
                ensureInteraction(cityState, context.world());
                return context;
            } finally {
                releaseLease(cityState, context);
            }
        })).thenCompose(context -> supplyAsync(() -> {
            context.operation().phase("CLEANUP_PENDING");
            Files.deleteIfExists(context.backup());
            context.operation().complete();
            context.operation().remove();
            return context;
        })).thenCompose(context -> this.<Void>onMain(() -> {
            Bukkit.getPluginManager().callEvent(new WishTreeLevelChangedEvent(cityState, context.previousLevel(), level));
            return null;
        })).exceptionallyCompose(error -> recoverFailedUpgrade(cityState, active.get(), error));
    }

    private CompletionStage<Void> recoverFailedUpgrade(CityState cityState, UpgradeContext context, Throwable failure) {
        WishTreeState state = store.get(cityState);
        if (context != null && context.operation().committed()) {
            releaseLease(cityState, context);
            return supplyAsync(() -> {
                context.operation().phase("CLEANUP_PENDING");
                Files.deleteIfExists(context.backup());
                context.operation().complete();
                context.operation().remove();
                PluginLogger.warning("许愿树升级已提交，但后处理曾失败，现已安全完成: " + rootMessage(failure));
                return null;
            });
        }
        CompletionStage<Void> rollback;
        if (context != null && Files.isRegularFile(context.backup())) {
            rollback = supplyAsync(() -> {
                context.operation().phase("ROLLING_BACK");
                paste(context.adapted(), context.backup(), treeOrigin());
                return null;
            }).thenCompose(ignored -> this.<Void>onMain(() -> {
                context.world().save();
                context.operation().phase("ROLLED_BACK");
                return null;
            }));
        } else {
            rollback = CompletableFuture.completedFuture(null);
        }
        return rollback.handle((ignored, rollbackFailure) -> {
            if (rollbackFailure == null) {
                state.failVisualUpgrade(rootMessage(failure));
            } else {
                state.failVisualUpgrade(rootMessage(failure) + "; rollback=" + rootMessage(rollbackFailure));
                if (context != null) context.operation().phase("ROLLBACK_FAILED");
            }
            return rollbackFailure;
        }).thenCompose(rollbackFailure -> this.<Void>onMain(() -> {
            if (context != null) releaseLease(cityState, context);
            IllegalStateException result = new IllegalStateException(
                    "许愿树建筑升级失败: " + rootMessage(failure), failure);
            if (rollbackFailure != null) result.addSuppressed(rollbackFailure);
            throw result;
        }));
    }

    private void releaseLease(CityState cityState, UpgradeContext context) {
        if (context.leaseHeld().compareAndSet(true, false)) worlds.releaseMaintenanceLease(cityState.getWorldName());
    }

    @Override
    public void ensureInteraction(CityState cityState, World world) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> ensureInteraction(cityState, world));
            return;
        }
        String cityId = cityState.getUuid().toString();
        List<Interaction> hits = new ArrayList<>();
        List<TextDisplay> labels = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(interactionLocation(world), 3D, 3D, 3D)) {
            if (!cityId.equals(entity.getPersistentDataContainer().get(cityKey, PersistentDataType.STRING))) continue;
            if (entity instanceof Interaction interaction) hits.add(interaction);
            if (entity instanceof TextDisplay display) labels.add(display);
        }
        keepOne(hits);
        keepOne(labels);
        if (hits.isEmpty()) {
            Interaction interaction = world.spawn(interactionLocation(world), Interaction.class);
            interaction.setInteractionWidth(1.4F);
            interaction.setInteractionHeight(2.2F);
            interaction.setResponsive(true);
            tag(interaction, cityId, "interaction");
        }
        if (labels.isEmpty()) {
            Location labelLocation = interactionLocation(world).add(0D, 2.4D, 0D);
            TextDisplay display = world.spawn(labelLocation, TextDisplay.class);
            display.text(Component.text("§d右键许愿树"));
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            tag(display, cityId, "label");
        }
    }

    @Override
    public void removeInteraction(CityState cityState) {
        World world = Bukkit.getWorld(cityState.getWorldName());
        if (world == null) return;
        String cityId = cityState.getUuid().toString();
        for (Entity entity : world.getEntities()) {
            if (cityId.equals(entity.getPersistentDataContainer().get(cityKey, PersistentDataType.STRING))) entity.remove();
        }
    }

    public UUID interactionCity(Entity entity) {
        String value = entity.getPersistentDataContainer().get(cityKey, PersistentDataType.STRING);
        if (value == null) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    private void tag(Entity entity, String cityId, String kind) {
        entity.getPersistentDataContainer().set(cityKey, PersistentDataType.STRING, cityId);
        entity.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind);
        entity.setPersistent(true);
    }

    private <T extends Entity> void keepOne(List<T> values) {
        for (int i = 1; i < values.size(); i++) values.get(i).remove();
    }

    private Location interactionLocation(World world) {
        return new Location(world, MainSettings.getWishTreeInteractionX(), MainSettings.getWishTreeInteractionY(),
                MainSettings.getWishTreeInteractionZ());
    }

    private void createPlaceholderSchematics(Path root) throws Exception {
        Path main = root.resolve(MainSettings.getWishTreeMainSchematic()).normalize();
        requireInside(root, main);
        if (!Files.exists(main)) write(main, createMainClipboard());
        BlockVector3 sharedOrigin = null;
        for (int level = 1; level <= 5; level++) {
            Path tree = root.resolve(MainSettings.getWishTreeSchematicPattern().replace("{level}", String.valueOf(level))).normalize();
            requireInside(root, tree);
            if (!Files.exists(tree)) write(tree, createTreeClipboard(level));
            try (InputStream input = Files.newInputStream(tree); ClipboardReader reader = reader(tree, input)) {
                Clipboard clipboard = reader.read();
                if (sharedOrigin == null) sharedOrigin = clipboard.getOrigin();
                validateTreeClipboard(tree, clipboard, sharedOrigin);
            }
        }
    }

    private void validateTreeClipboard(Path file, Clipboard clipboard, BlockVector3 sharedOrigin) {
        if (!clipboard.getDimensions().equals(BlockVector3.at(15, 24, 15))) {
            throw new IllegalStateException("许愿树结构尺寸必须为 15x24x15: " + file);
        }
        if (!clipboard.getOrigin().equals(sharedOrigin)) {
            throw new IllegalStateException("五阶许愿树结构必须使用相同原点: " + file);
        }
        if (!clipboard.getEntities().isEmpty()) {
            throw new IllegalStateException("许愿树结构不得包含实体: " + file);
        }
        java.util.Set<String> forbidden = java.util.Set.of(
                "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:hopper",
                "minecraft:dispenser", "minecraft:dropper", "minecraft:furnace", "minecraft:blast_furnace",
                "minecraft:smoker", "minecraft:brewing_stand", "minecraft:spawner", "minecraft:crafter",
                "minecraft:chiseled_bookshelf", "minecraft:lectern", "minecraft:jukebox");
        for (BlockVector3 position : clipboard.getRegion()) {
            String blockId = clipboard.getBlock(position).getBlockType().getId();
            if (forbidden.contains(blockId) || blockId.endsWith("_shulker_box")) {
                throw new IllegalStateException("许愿树结构不得包含容器或刷怪笼: " + blockId + " @ " + position);
            }
        }
    }

    private Clipboard createMainClipboard() {
        CuboidRegion region = new CuboidRegion(BlockVector3.at(-16, 0, -16), BlockVector3.at(16, 12, 16));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(BlockVector3.ZERO);
        BlockState bricks = BlockTypes.STONE_BRICKS.getDefaultState();
        BlockState quartz = BlockTypes.SMOOTH_QUARTZ.getDefaultState();
        BlockState glass = BlockTypes.GLASS.getDefaultState();
        for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) clipboard.setBlock(BlockVector3.at(x, 0, z), bricks);
        for (int x = -8; x <= 8; x++) for (int z = -15; z <= -3; z++) clipboard.setBlock(BlockVector3.at(x, 1, z), quartz);
        for (int y = 2; y <= 8; y++) for (int x = -8; x <= 8; x++) for (int z = -15; z <= -3; z++) {
            if (x == -8 || x == 8 || z == -15 || z == -3) clipboard.setBlock(BlockVector3.at(x, y, z),
                    y >= 4 && y <= 6 && (Math.abs(x) % 4 == 0 || z == -15) ? glass : bricks);
        }
        for (int x = -8; x <= 8; x++) for (int z = -15; z <= -3; z++) clipboard.setBlock(BlockVector3.at(x, 9, z), quartz);
        for (int y = 2; y <= 4; y++) for (int x = -1; x <= 1; x++) clipboard.setBlock(BlockVector3.at(x, y, -3), BlockTypes.AIR.getDefaultState());
        return clipboard;
    }

    private Clipboard createTreeClipboard(int level) {
        CuboidRegion region = new CuboidRegion(BlockVector3.at(-7, 0, -7), BlockVector3.at(7, 23, 7));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(BlockVector3.ZERO);
        BlockState log = BlockTypes.OAK_LOG.getDefaultState();
        BlockState leaves = level >= 4 ? BlockTypes.AZALEA_LEAVES.getDefaultState() : BlockTypes.OAK_LEAVES.getDefaultState();
        int height = 7 + level * 2;
        int radius = 2 + level;
        for (int y = 0; y <= height; y++) clipboard.setBlock(BlockVector3.at(0, y, 0), log);
        for (int y = Math.max(4, height - 6); y <= height + 3; y++) {
            int layerRadius = Math.max(2, radius - Math.abs(height - y) / 2);
            for (int x = -layerRadius; x <= layerRadius; x++) for (int z = -layerRadius; z <= layerRadius; z++) {
                if (x * x + z * z <= layerRadius * layerRadius + 2) clipboard.setBlock(BlockVector3.at(x, y, z), leaves);
            }
        }
        if (level >= 2) for (int x = -2; x <= 2; x++) clipboard.setBlock(BlockVector3.at(x, height - 3, 0), log);
        if (level >= 3) clipboard.setBlock(BlockVector3.at(0, height + 2, 0), BlockTypes.GLOWSTONE.getDefaultState());
        if (level >= 4) for (int x : List.of(-3, 3)) clipboard.setBlock(BlockVector3.at(x, height, 0), BlockTypes.AMETHYST_BLOCK.getDefaultState());
        if (level >= 5) for (int z : List.of(-3, 3)) clipboard.setBlock(BlockVector3.at(0, height, z), BlockTypes.SEA_LANTERN.getDefaultState());
        return clipboard;
    }

    private void snapshotAssets(Path active, Path snapshot) throws Exception {
        Files.createDirectories(snapshot);
        Path manifestPath = snapshot.resolve("manifest.yml");
        YamlConfiguration manifest = YamlFiles.load(manifestPath.toFile());
        List<String> relative = new ArrayList<>();
        relative.add(MainSettings.getWishTreeMainSchematic());
        for (int level = 1; level <= 5; level++) relative.add(MainSettings.getWishTreeSchematicPattern().replace("{level}", String.valueOf(level)));
        if (!Files.exists(manifestPath)) {
            manifest.set("template_revision", MainSettings.getCityStateWorldTemplateRevision());
            for (String name : relative) {
                Path source = active.resolve(name).normalize();
                Path target = snapshot.resolve(name).normalize();
                requireInside(snapshot, target);
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                manifest.set("files." + key(name), sha256(target));
            }
            YamlFiles.save(manifest, manifestPath.toFile());
        } else {
            if (manifest.getInt("template_revision") != MainSettings.getCityStateWorldTemplateRevision()) {
                throw new IllegalStateException("FAWE 结构快照修订号不匹配");
            }
            for (String name : relative) {
                Path target = snapshot.resolve(name).normalize();
                if (!Files.isRegularFile(target) || !sha256(target).equals(manifest.getString("files." + key(name)))) {
                    throw new IllegalStateException("FAWE 不可变结构快照已被修改，请提升 template_revision: " + name);
                }
            }
        }
    }

    private void paste(com.sk89q.worldedit.world.World world, Path file, BlockVector3 target) throws Exception {
        try (InputStream input = Files.newInputStream(file); ClipboardReader reader = reader(file, input);
             EditSession editSession = WorldEdit.getInstance().newEditSession(world)) {
            Clipboard clipboard = reader.read();
            ClipboardHolder holder = new ClipboardHolder(clipboard);
            Operations.complete(holder.createPaste(editSession).to(target).ignoreAirBlocks(false).build());
            editSession.flushSession();
        }
    }

    private void backup(com.sk89q.worldedit.world.World world, Path file) throws Exception {
        BlockVector3 origin = treeOrigin();
        CuboidRegion region = new CuboidRegion(origin.add(-7, 0, -7), origin.add(7, 23, 7));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(origin);
        Operations.complete(new ForwardExtentCopy(world, region, clipboard, region.getMinimumPoint()));
        write(file, clipboard);
    }

    private void write(Path file, Clipboard clipboard) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp-" + UUID.randomUUID());
        try (OutputStream output = Files.newOutputStream(temporary);
             ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(output)) {
            writer.write(clipboard);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private ClipboardReader reader(Path file, InputStream input) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(file.toFile());
        if (format == null) throw new IllegalArgumentException("无法识别 schematic 格式: " + file);
        return format.getReader(input);
    }

    private Path schematic(CityState city, String relative) {
        Path root = snapshotRoot(city.getYaml().getInt("world.template.revision", MainSettings.getCityStateWorldTemplateRevision()));
        Path file = root.resolve(relative).normalize();
        requireInside(root, file);
        if (!Files.isRegularFile(file)) throw new IllegalStateException("结构文件不存在: " + relative);
        return file;
    }

    private Path treeSchematic(CityState city, int level) {
        return schematic(city, MainSettings.getWishTreeSchematicPattern().replace("{level}", String.valueOf(level)));
    }
    private BlockVector3 treeOrigin() { return BlockVector3.at(MainSettings.getWishTreeOriginX(), MainSettings.getWishTreeOriginY(), MainSettings.getWishTreeOriginZ()); }
    private Path activeRoot() { return plugin.getDataFolder().toPath().resolve("schematics").toAbsolutePath().normalize(); }
    private Path snapshotRoot() { return snapshotRoot(MainSettings.getCityStateWorldTemplateRevision()); }
    private Path snapshotRoot(int revision) { return plugin.getDataFolder().toPath().resolve("data/schematic_snapshots/" + revision).toAbsolutePath().normalize(); }
    private String key(String path) { return path.replace('.', '_').replace('/', '_').replace('\\', '_'); }

    private void requireInside(Path root, Path candidate) {
        if (!candidate.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("结构路径越界: " + candidate);
        }
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private <T> CompletableFuture<T> supplyAsync(ThrowingSupplier<T> supplier) {
        return worlds.runIoOperation(supplier::get).toCompletableFuture();
    }

    private <T> CompletionStage<T> onMain(ThrowingSupplier<T> supplier) {
        if (Bukkit.isPrimaryThread()) {
            try { return CompletableFuture.completedFuture(supplier.get()); }
            catch (Throwable error) { return CompletableFuture.failedFuture(error); }
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try { result.complete(supplier.get()); } catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result;
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current instanceof java.util.concurrent.CompletionException) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @Override public void shutdown() { }

    @FunctionalInterface private interface ThrowingSupplier<T> { T get() throws Exception; }
    private record UpgradeContext(World world, com.sk89q.worldedit.world.World adapted, WishTreeState state,
                                  VisualOperation operation, Path backup, int previousLevel,
                                  AtomicBoolean leaseHeld) { }
    private record RecoveryContext(World world, com.sk89q.worldedit.world.World adapted,
                                   VisualOperation operation, Path backup, WishTreeState state,
                                   AtomicBoolean leaseHeld) { }

    private static final class VisualOperation {
        private final File file;
        private final UUID id;
        private final YamlConfiguration yaml;
        private VisualOperation(File file, UUID id, YamlConfiguration yaml) { this.file = file; this.id = id; this.yaml = yaml; }
        static VisualOperation create(RookieCityState plugin, CityState city, int from, int to) {
            UUID id = UUID.randomUUID();
            File file = new File(plugin.getDataFolder(), "data/operations/wish-tree/" + id + ".yml");
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("id", id.toString()); yaml.set("city_state_uuid", city.getUuid().toString());
            yaml.set("world", city.getWorldName()); yaml.set("from_level", from); yaml.set("target_level", to);
            yaml.set("phase", "PREPARED"); yaml.set("updated_at", System.currentTimeMillis());
            YamlFiles.save(yaml, file);
            return new VisualOperation(file, id, yaml);
        }
        void phase(String phase) { yaml.set("phase", phase); yaml.set("updated_at", System.currentTimeMillis()); YamlFiles.save(yaml, file); }
        void complete() { phase("COMPLETE"); }
        void remove() {
            if (!file.delete() && file.exists()) throw new IllegalStateException("无法删除已完成的许愿树视觉操作 " + id);
        }
        static VisualOperation load(File file) {
            try {
                YamlConfiguration yaml = YamlFiles.load(file);
                String id = yaml.getString("id");
                return id == null ? null : new VisualOperation(file, UUID.fromString(id), yaml);
            } catch (RuntimeException error) {
                PluginLogger.warning("无法读取许愿树视觉操作 " + file.getName() + ": " + error.getMessage());
                return null;
            }
        }
        UUID cityStateId() { return UUID.fromString(yaml.getString("city_state_uuid")); }
        String phase() { return yaml.getString("phase", "PREPARED"); }
        int targetLevel() { return yaml.getInt("target_level"); }
        boolean committed() {
            return phase().equals("STATE_COMMITTED") || phase().equals("CLEANUP_PENDING") || phase().equals("COMPLETE");
        }
        File file() { return file; }
        UUID id() { return id; }
    }
}
