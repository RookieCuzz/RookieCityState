package com.cuzz.rookiecitystate.world;

import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class WorldGuardCityWorldProtectionService implements CityWorldProtectionService {
    public static final String AREA_REGION_ID = "city_area";
    private TemplateRegion templateRegion;

    @Override
    public CompletionStage<Void> ensureTemplateCore(World template, String coreRegionId,
                                                    int minX, int minY, int minZ,
                                                    int maxX, int maxY, int maxZ) {
        return execute(() -> {
            requirePrimaryThread();
            RegionManager manager = manager(template);
            if (manager.getRegion(coreRegionId) != null) return;
            ProtectedCuboidRegion core = new ProtectedCuboidRegion(coreRegionId,
                    BlockVector3.at(minX, minY, minZ), BlockVector3.at(maxX, maxY, maxZ));
            core.setPriority(20);
            core.setFlag(Flags.BUILD, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            core.setFlag(Flags.CHEST_ACCESS, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            core.setFlag(Flags.PISTONS, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            core.setFlag(Flags.WATER_FLOW, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            core.setFlag(Flags.LAVA_FLOW, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            core.setFlag(Flags.ENTITY_ITEM_FRAME_DESTROY, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            core.setFlag(Flags.ENTITY_PAINTING_DESTROY, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            manager.addRegion(core);
            manager.save();
        });
    }

    @Override
    public void captureTemplate(World template, String coreRegionId) {
        requirePrimaryThread();
        ProtectedRegion source = manager(template).getRegion(coreRegionId);
        if (source == null) throw new IllegalStateException("Template is missing WorldGuard region: " + coreRegionId);
        if (source.getParent() != null) throw new IllegalStateException("Template core region must not have a parent");
        if (source.hasMembersOrOwners()) throw new IllegalStateException("Template core region must not have members or owners");
        if (!(source instanceof ProtectedCuboidRegion) && !(source instanceof ProtectedPolygonalRegion)) {
            throw new IllegalStateException("Template core region must be cuboid or polygonal");
        }
        templateRegion = TemplateRegion.capture(source);
    }

    @Override
    public boolean hasTemplateSnapshot() {
        return templateRegion != null;
    }

    @Override
    public CompletionStage<Void> installTemplateSnapshot(World snapshot) {
        return execute(() -> {
            requirePrimaryThread();
            if (templateRegion == null) throw new IllegalStateException("Template core region has not been captured");
            RegionManager manager = manager(snapshot);
            manager.addRegion(templateRegion.instantiate());
            manager.save();
        });
    }

    @Override
    public CompletionStage<Void> apply(CityState cityState, World world, int borderSize) {
        return execute(() -> {
            requirePrimaryThread();
            if (templateRegion == null) throw new IllegalStateException("Template core region has not been validated");
            double half = borderSize / 2.0D;
            double centerX = world.getWorldBorder().getCenter().getX();
            double centerZ = world.getWorldBorder().getCenter().getZ();
            BlockVector3 min = BlockVector3.at((int) Math.floor(centerX - half), world.getMinHeight(),
                    (int) Math.floor(centerZ - half));
            BlockVector3 max = BlockVector3.at((int) Math.ceil(centerX + half) - 1, world.getMaxHeight() - 1,
                    (int) Math.ceil(centerZ + half) - 1);

            RegionManager manager = manager(world);
            ProtectedCuboidRegion area = new ProtectedCuboidRegion(AREA_REGION_ID, min, max);
            setDomains(area, cityState);
            manager.addRegion(area);

            ProtectedRegion core = templateRegion.instantiate();
            core.setPriority(Math.max(core.getPriority(), area.getPriority() + 10));
            core.setFlag(Flags.BUILD, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            manager.addRegion(core);

            BlockVector3 treeMin = BlockVector3.at(MainSettings.getWishTreeOriginX() - 7,
                    MainSettings.getWishTreeOriginY(), MainSettings.getWishTreeOriginZ() - 7);
            BlockVector3 treeMax = BlockVector3.at(MainSettings.getWishTreeOriginX() + 7,
                    MainSettings.getWishTreeOriginY() + 23, MainSettings.getWishTreeOriginZ() + 7);
            ProtectedCuboidRegion tree = new ProtectedCuboidRegion(MainSettings.getWishTreeRegionId(), treeMin, treeMax);
            tree.setPriority(Math.max(core.getPriority() + 10, area.getPriority() + 20));
            tree.setFlag(Flags.BUILD, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            tree.setFlag(Flags.CHEST_ACCESS, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            tree.setFlag(Flags.PISTONS, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            tree.setFlag(Flags.WATER_FLOW, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            tree.setFlag(Flags.LAVA_FLOW, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            tree.setFlag(Flags.ITEM_DROP, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            tree.setFlag(Flags.ITEM_PICKUP, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            tree.setFlag(Flags.ENTITY_ITEM_FRAME_DESTROY, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            tree.setFlag(Flags.ENTITY_PAINTING_DESTROY, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            manager.addRegion(tree);

            ProtectedRegion global = manager.getRegion(ProtectedRegion.GLOBAL_REGION);
            if (global == null) {
                global = new GlobalProtectedRegion(ProtectedRegion.GLOBAL_REGION);
                manager.addRegion(global);
            }
            global.setFlag(Flags.PVP, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            manager.save();
        });
    }

    @Override
    public CompletionStage<Void> synchronizeMembers(CityState cityState) {
        return execute(() -> {
            requirePrimaryThread();
            World world = Bukkit.getWorld(cityState.getWorldName());
            if (world == null) return;
            RegionManager manager = manager(world);
            ProtectedRegion area = manager.getRegion(AREA_REGION_ID);
            if (area == null) throw new IllegalStateException("Managed world is missing city_area: " + world.getName());
            setDomains(area, cityState);
            manager.saveChanges();
        });
    }

    @Override
    public CompletionStage<Void> removeWorld(String worldName) {
        return execute(() -> {
            requirePrimaryThread();
            World world = Bukkit.getWorld(worldName);
            if (world == null) return;
            RegionManager manager = manager(world);
            manager.removeRegion(MainSettings.getWishTreeRegionId());
            manager.removeRegion(MainSettings.getCityStateWorldCoreRegion());
            manager.removeRegion(AREA_REGION_ID);
            manager.saveChanges();
        });
    }

    private void setDomains(ProtectedRegion region, CityState cityState) {
        DefaultDomain owners = new DefaultDomain();
        owners.addPlayer(cityState.getOwner().getUuid());
        DefaultDomain members = new DefaultDomain();
        for (CityStateMember member : cityState.getMembers()) {
            if (!member.getUuid().equals(cityState.getOwner().getUuid())) members.addPlayer(member.getUuid());
        }
        region.setOwners(owners);
        region.setMembers(members);
    }

    private RegionManager manager(World world) {
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        if (manager == null) throw new IllegalStateException("WorldGuard has no region manager for " + world.getName());
        return manager;
    }

    private void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("WorldGuard operations must run on the server thread");
    }

    private CompletionStage<Void> execute(CheckedAction action) {
        try {
            action.run();
            return CompletableFuture.completedFuture(null);
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }

    private record TemplateRegion(
            String id,
            boolean polygonal,
            BlockVector3 minimum,
            BlockVector3 maximum,
            List<BlockVector2> points,
            int priority,
            Map<Flag<?>, Object> flags
    ) {
        static TemplateRegion capture(ProtectedRegion source) {
            return new TemplateRegion(source.getId(), source instanceof ProtectedPolygonalRegion,
                    source.getMinimumPoint(), source.getMaximumPoint(), new ArrayList<>(source.getPoints()),
                    source.getPriority(), new HashMap<>(source.getFlags()));
        }

        ProtectedRegion instantiate() {
            ProtectedRegion result = polygonal
                    ? new ProtectedPolygonalRegion(id, points, minimum.y(), maximum.y())
                    : new ProtectedCuboidRegion(id, minimum, maximum);
            result.setPriority(priority);
            result.setFlags(new HashMap<>(flags));
            result.setOwners(new DefaultDomain());
            result.setMembers(new DefaultDomain());
            return result;
        }
    }
}
