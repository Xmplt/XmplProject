package me.xmpl.lib.hook;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

public final class WorldGuardHook {
    private WorldGuardHook() {}

    private static WorldGuardPlugin getWG() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (plugin instanceof WorldGuardPlugin && plugin.isEnabled()) {
            return (WorldGuardPlugin) plugin;
        }
        return null;
    }

    /**
     * Проверяет, свободна ли точка от любых регионов (кроме __global__)
     */
    public static boolean isLocationFreeOfRegions(Location loc) {
        WorldGuardPlugin wg = getWG();
        if (wg == null || loc == null || loc.getWorld() == null) return true;

        RegionManager rm = wg.getRegionManager(loc.getWorld());
        if (rm == null) return true;

        ApplicableRegionSet set = rm.getApplicableRegions(loc);
        return set.size() == 0;
    }

    /**
     * Проверяет, находится ли локация внутри конкретного региона WorldGuard (например "spawn1")
     */
    public static boolean isLocationInRegion(Location loc, String regionName) {
        if (regionName == null || regionName.isEmpty()) return true;
        WorldGuardPlugin wg = getWG();
        if (wg == null || loc == null || loc.getWorld() == null) return false;

        RegionManager rm = wg.getRegionManager(loc.getWorld());
        if (rm == null) return false;

        ApplicableRegionSet set = rm.getApplicableRegions(loc);
        for (ProtectedRegion region : set) {
            if (region.getId().equalsIgnoreCase(regionName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Возвращает центральную точку указанного региона
     */
    public static Location getRegionCenter(World world, String regionName) {
        WorldGuardPlugin wg = getWG();
        if (wg == null || world == null || regionName == null) return world != null ? world.getSpawnLocation() : null;

        RegionManager rm = wg.getRegionManager(world);
        if (rm == null) return world.getSpawnLocation();

        ProtectedRegion region = rm.getRegion(regionName);
        if (region == null) return world.getSpawnLocation();

        BlockVector min = region.getMinimumPoint();
        BlockVector max = region.getMaximumPoint();
        double centerX = (min.getX() + max.getX()) / 2.0;
        double centerZ = (min.getZ() + max.getZ()) / 2.0;
        double centerY = world.getHighestBlockYAt((int) centerX, (int) centerZ);

        return new Location(world, centerX, centerY, centerZ);
    }
}