package me.xmpl.airdrop.service;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import me.xmpl.airdrop.AirDropExtension;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class ProtectiveRegionService {
    private final AirDropExtension extension;

    public ProtectiveRegionService(AirDropExtension extension) {
        this.extension = extension;
    }

    private WorldGuardPlugin getWG() {
        Plugin p = Bukkit.getPluginManager().getPlugin("WorldGuard");
        return (p instanceof WorldGuardPlugin && p.isEnabled()) ? (WorldGuardPlugin) p : null;
    }

    public String create5x5Region(Location chestLoc) {
        WorldGuardPlugin wg = getWG();
        if (wg == null || chestLoc == null || chestLoc.getWorld() == null) return null;

        RegionManager rm = wg.getRegionManager(chestLoc.getWorld());
        if (rm == null) return null;

        // Радиус 2 блока вокруг сундука дает квадрат ровно 5x5 блоков!
        int radius = extension.getConfig().getInt("settings.protective-region-radius", 2);
        int up = extension.getConfig().getInt("settings.protective-region-height-up", 6);
        int down = extension.getConfig().getInt("settings.protective-region-height-down", 4);

        BlockVector min = new BlockVector(
                chestLoc.getBlockX() - radius,
                Math.max(1, chestLoc.getBlockY() - down),
                chestLoc.getBlockZ() - radius
        );
        BlockVector max = new BlockVector(
                chestLoc.getBlockX() + radius,
                Math.min(255, chestLoc.getBlockY() + up),
                chestLoc.getBlockZ() + radius
        );

        String regionId = "xmpl_airdrop_" + UUID.randomUUID().toString().substring(0, 8);
        ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);
        region.setPriority(50);

        region.setFlag(DefaultFlag.PVP, StateFlag.State.ALLOW);
        region.setFlag(DefaultFlag.BLOCK_BREAK, StateFlag.State.DENY);
        region.setFlag(DefaultFlag.BLOCK_PLACE, StateFlag.State.DENY);
        region.setFlag(DefaultFlag.USE, StateFlag.State.ALLOW);
        region.setFlag(DefaultFlag.CHEST_ACCESS, StateFlag.State.DENY);
        region.setFlag(DefaultFlag.TNT, StateFlag.State.DENY);
        region.setFlag(DefaultFlag.CREEPER_EXPLOSION, StateFlag.State.DENY);
        region.setFlag(DefaultFlag.OTHER_EXPLOSION, StateFlag.State.DENY);

        rm.addRegion(region);
        return regionId;
    }

    public void removeRegion(Location chestLoc, String regionId) {
        if (chestLoc == null || chestLoc.getWorld() == null || regionId == null || regionId.isEmpty()) return;
        WorldGuardPlugin wg = getWG();
        if (wg == null) return;

        RegionManager rm = wg.getRegionManager(chestLoc.getWorld());
        if (rm != null && rm.getRegion(regionId) != null) {
            rm.removeRegion(regionId);
        }
    }
}